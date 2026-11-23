package com.shanghai.memory.data

import com.shanghai.memory.data.llm.ChatClient
import com.shanghai.memory.data.model.RoutePlan
import com.shanghai.memory.data.model.RouteStop
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONObject

class RouteRepository(
    private val settingsStore: SettingsStore,
    private val chat: ChatClient = ChatClient(),
    private val amap: AmapService? = null,
    private val search: com.shanghai.memory.data.llm.SearchClient =
        com.shanghai.memory.data.llm.SearchClient()
) {

    private companion object {
        /**
         * 4–6 站、每站三个中文字段，1600 很容易写到一半就被截断，
         * 截出来的 JSON 缺右括号，解析必然失败——这是「路线格式无法解析」的主因。
         */
        const val MAX_TOKENS = 3000
    }

    fun presets(): List<RoutePlan> = CuratedRoutes.presets()

    suspend fun generate(
        keywords: String,
        hours: Int?,
        notes: String = "",
        fixedStops: List<String> = emptyList()
    ): RoutePlan = withContext(Dispatchers.IO) {
        val settings = settingsStore.current()
        if (!settings.hasKey) throw IllegalStateException("请先在设置里填写 API Key")

        // 路线要「最近能去哪」：先按话题找到近期活动的公开资料，再让模型在真实活动上排站点
        val activities = activityBlock(settings, keywords, notes)
        val system = PromptTemplates.routeSystem()
        val prompt = PromptTemplates.routeUserPrompt(keywords, hours, notes, activities, fixedStops)

        // 先按 JSON 模式要一次
        val first = ask(settings, system, prompt, jsonMode = true)
        first?.let { parse(it) }?.let { return@withContext it }

        // 再退一步：不用 JSON 模式，只要它老老实实吐一个对象出来
        val retry = ask(
            settings = settings,
            system = system,
            userPrompt = prompt + "\n\n再次强调：只输出上面那一个 JSON 对象，第一个字符是 {，最后一个字符是 }。",
            jsonMode = false
        )
        retry?.let { parse(it) }?.let { return@withContext it }

        val raw = (retry ?: first).orEmpty()
        throw IllegalStateException(
            if (raw.isBlank()) {
                "模型没有返回内容，请重试一次"
            } else {
                "模型返回的路线读不出来（收到 ${raw.length} 字，已自动重试一次）。开头是：${raw.take(60)}"
            }
        )
    }

    /**
     * 近期活动资料块。检索失败或没配检索能力就返回空串，
     * prompt 里会退化成「没有查到确定的活动」，绝不让模型编日期。
     */
    private suspend fun activityBlock(
        settings: AppSettings,
        keywords: String,
        notes: String
    ): String {
        if (!settings.canSearch) return ""
        val topic = TopicRouting.detect(keywords, notes)
        val hits = search.search(
            settings = settings,
            query = TopicRouting.recentActivityQuery(keywords),
            count = 6,
            topic = topic
        )
        if (hits.isEmpty()) return ""
        return hits.take(5)
            .mapIndexed { index, hit -> hit.toPromptBlock(index + 1) }
            .joinToString("\n")
    }

    /** 单次请求，异常不上抛，交给上层决定重试；协程取消照常传播 */
    private suspend fun ask(
        settings: AppSettings,
        system: String,
        userPrompt: String,
        jsonMode: Boolean
    ): String? = try {
        chat.complete(
            settings = settings,
            systemPrompt = system,
            userPrompt = userPrompt,
            maxTokens = MAX_TOKENS,
            jsonMode = jsonMode
        )
    } catch (c: CancellationException) {
        throw c
    } catch (t: Throwable) {
        null
    }

    /**
     * 把站点名换成坐标。地理编码逐个并发发出，单个失败只影响那一站：
     * 没有坐标的站点仍在文字方案里，只是不参与画线与距离向导。
     */
    suspend fun locate(plan: RoutePlan): RoutePlan = coroutineScope {
        val service = amap ?: return@coroutineScope plan
        val stops = plan.stops.map { stop ->
            async {
                if (stop.located) return@async stop
                val keyword = listOf("上海", stop.area, stop.name)
                    .filter { it.isNotBlank() }
                    .joinToString(" ")
                val point = service.geocode(keyword)
                if (point == null) stop else stop.copy(lat = point.first, lng = point.second)
            }
        }.awaitAll()
        plan.copy(stops = stops)
    }

    /**
     * 自定义路线第一步：按用户关键词与偏好在真实上海找出候选地点。
     * 只出名字与介绍，坐标由 [locateSpots] 事后补齐 —— 让模型编坐标必然错。
     */
    suspend fun searchSpots(
        keywords: String,
        preference: String,
        want: Int = 10
    ): List<SpotCandidate> = withContext(Dispatchers.IO) {
        val settings = settingsStore.current()
        if (!settings.hasKey) throw IllegalStateException("请先在设置里填写 API Key")
        val system = PromptTemplates.spotSystem()
        val prompt = PromptTemplates.spotUserPrompt(keywords, preference, want)

        val first = ask(settings, system, prompt, jsonMode = true)?.let { parseSpots(it) }
        if (!first.isNullOrEmpty()) return@withContext first

        val retry = ask(
            settings = settings,
            system = system,
            userPrompt = prompt + "\n\n再次强调：只输出上面那一个 JSON 对象，第一个字符是 {，最后一个字符是 }。",
            jsonMode = false
        )?.let { parseSpots(it) }
        retry.orEmpty()
    }

    private fun parseSpots(raw: String): List<SpotCandidate> {
        val text = stripFence(raw)
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start < 0 || end <= start) return emptyList()
        val body = text.substring(start, end + 1)
        val array = runCatching {
            JSONObject(body).optJSONArray("spots")
        }.getOrNull() ?: return salvageSpots(body)
        val list = ArrayList<SpotCandidate>()
        for (i in 0 until array.length()) {
            val o = array.optJSONObject(i) ?: continue
            val name = o.optString("name").trim()
            if (name.isBlank()) continue
            list.add(
                SpotCandidate(
                    name = name,
                    area = o.optString("area").trim(),
                    blurb = o.optString("blurb").trim()
                )
            )
        }
        return if (list.isEmpty()) salvageSpots(body) else list.distinctBy { it.name }
    }

    /** 输出被截断时，把已经写完整的对象逐个捞出来 */
    private fun salvageSpots(text: String): List<SpotCandidate> {
        val list = ArrayList<SpotCandidate>()
        val nameKey = "\"name\""
        var from = 0
        while (true) {
            val at = text.indexOf(nameKey, from)
            if (at < 0) break
            val segment = text.substring(at, minOf(text.length, at + 400))
            val name = field(segment, "name")
            if (name.isNotBlank()) {
                list.add(
                    SpotCandidate(
                        name = name,
                        area = field(segment, "area"),
                        blurb = field(segment, "blurb")
                    )
                )
            }
            from = at + nameKey.length
        }
        return list.distinctBy { it.name }
    }

    /** 给候选地点补坐标并算到用户当前位置的距离，失败的保留文字、只是不显示距离 */
    suspend fun locateSpots(
        spots: List<SpotCandidate>,
        fromLat: Double,
        fromLng: Double
    ): List<SpotCandidate> = coroutineScope {
        val service = amap ?: return@coroutineScope spots
        spots.map { spot ->
            async {
                if (spot.located) return@async spot
                val keyword = listOf("上海", spot.area, spot.name)
                    .filter { it.isNotBlank() }
                    .joinToString(" ")
                val point = service.geocode(keyword)
                    ?: service.searchByKeyword(spot.name, limit = 1).firstOrNull()
                        ?.let { it.lat to it.lng }
                if (point == null) spot else spot.locatedAt(point.first, point.second)
            }
        }.awaitAll().map { spot ->
            if (fromLat == 0.0 && fromLng == 0.0) spot else spot.withDistance(fromLat, fromLng)
        }
    }

    private fun parse(raw: String): RoutePlan? {
        val text = stripFence(raw)
        val start = text.indexOf('{')
        if (start < 0) return null
        val end = text.lastIndexOf('}')
        if (end > start) {
            val direct = runCatching {
                buildPlan(JSONObject(text.substring(start, end + 1)))
            }.getOrNull()
            if (direct != null) return direct
        }

        // 走到这里通常是输出被截断：把已经写完整的那几站救回来，别让整条路线作废
        val stops = salvageStops(text)
        if (stops.isEmpty()) return null
        return RoutePlan(
            routeName = field(text, "route_name").ifBlank { "AI 定制路线" },
            theme = field(text, "theme"),
            stops = stops,
            transitionLogic = field(text, "transition_logic"),
            totalMin = stops.sumOf { it.stayMin } + stops.size * 10
        )
    }

    private fun stripFence(raw: String): String {
        var text = raw.trim()
        if (text.startsWith("```")) {
            text = text.substringAfter('\n', text)
            text = text.substringBeforeLast("```", text)
        }
        return text.trim()
    }

    private fun buildPlan(obj: JSONObject): RoutePlan? {
        val arr = obj.optJSONArray("stops") ?: return null
        val stops = (0 until arr.length()).mapNotNull { i ->
            toStop(arr.optJSONObject(i), i)
        }
        if (stops.isEmpty()) return null
        val estimated = stops.sumOf { it.stayMin } + stops.size * 10
        val total = obj.optInt("total_min", 0)
        return RoutePlan(
            routeName = obj.optString("route_name").trim().ifBlank { "AI 定制路线" },
            theme = obj.optString("theme").trim(),
            stops = stops,
            transitionLogic = obj.optString("transition_logic").trim(),
            totalMin = if (total > 0) total else estimated
        )
    }

    private fun toStop(o: JSONObject?, index: Int): RouteStop? {
        if (o == null) return null
        val name = o.optString("name").trim()
        if (name.isEmpty()) return null
        val stay = o.optInt("stay_min", 30)
        return RouteStop(
            seq = o.optInt("seq", index + 1),
            name = name,
            area = o.optString("area").trim(),
            stayMin = if (stay > 0) stay else 30,
            reason = o.optString("reason").trim(),
            storyHook = o.optString("story_hook").trim()
        )
    }

    /**
     * 扫描 stops 数组，按括号配对取出每一个已经闭合的对象。
     * 输出被截断时，最后那个写了一半的站会被丢掉，前面的站照常可用。
     */
    private fun salvageStops(text: String): List<RouteStop> {
        val stopsKey = text.indexOf("\"stops\"")
        if (stopsKey < 0) return emptyList()
        val arrStart = text.indexOf('[', stopsKey)
        if (arrStart < 0) return emptyList()

        val found = mutableListOf<RouteStop>()
        var depth = 0
        var inString = false
        var escaped = false
        var objStart = -1

        for (i in arrStart + 1 until text.length) {
            val c = text[i]
            if (inString) {
                when {
                    escaped -> escaped = false
                    c == '\\' -> escaped = true
                    c == '"' -> inString = false
                }
                continue
            }
            when (c) {
                '"' -> inString = true
                '{' -> {
                    if (depth == 0) objStart = i
                    depth++
                }

                '}' -> {
                    if (depth > 0) depth--
                    if (depth == 0 && objStart >= 0) {
                        val piece = text.substring(objStart, i + 1)
                        runCatching { JSONObject(piece) }.getOrNull()?.let { obj ->
                            toStop(obj, found.size)?.let { found += it }
                        }
                        objStart = -1
                    }
                }

                ']' -> if (depth == 0) break
            }
        }
        return found
    }

    private fun field(text: String, key: String): String =
        Regex("\"$key\"\\s*:\\s*\"([^\"]*)\"").find(text)?.groupValues?.get(1)?.trim().orEmpty()
}
