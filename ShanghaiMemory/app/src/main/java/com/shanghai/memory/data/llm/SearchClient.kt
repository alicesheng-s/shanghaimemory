package com.shanghai.memory.data.llm

import com.shanghai.memory.data.AppSettings
import com.shanghai.memory.data.TopicRouting
import com.shanghai.memory.data.model.SearchHit
import com.shanghai.memory.data.model.SearchKind
import com.shanghai.memory.data.text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

/**
 * 「先检索、后生成」里的检索段。
 *
 * DeepSeek 官方 Search API 返回的 summary（600–800 字）正好适合直接喂给模型，
 * 比让模型自己决定搜什么更可控，也让首字延迟可预测。
 */
class SearchClient(private val http: OkHttpClient = Http.quickClient) {

    companion object {
        private const val DEEPSEEK_SEARCH_URL = "https://api.deepseek.com/v1/tools/search"
        private const val ZHIPU_SEARCH_URL = "https://open.bigmodel.cn/api/paas/v4/web_search"
    }

    suspend fun search(
        settings: AppSettings,
        query: String,
        count: Int = 4,
        topic: TopicRouting.Topic = TopicRouting.Topic.GENERAL
    ): List<SearchHit> =
        withContext(Dispatchers.IO) {
            if (!settings.canSearch || query.isBlank()) return@withContext emptyList()
            runCatching {
                when {
                    // 自定义端点未必支持搜索引擎运算符，原样发
                    settings.customSearchUrl.isNotBlank() ->
                        customSearch(settings, query, count)

                    // 话题决定去哪些站点找：site: 运算符是官方 query 里支持的写法
                    settings.provider.searchKind == SearchKind.DEEPSEEK_TOOLS ->
                        deepSeekSearch(settings, TopicRouting.decorate(query, topic), count, query, topic)

                    settings.provider.searchKind == SearchKind.ZHIPU_WEB ->
                        zhipuSearch(settings, query, count)

                    else -> emptyList()
                }
            }.getOrElse { emptyList() }
        }

    private fun deepSeekSearch(
        settings: AppSettings,
        query: String,
        count: Int,
        original: String = query,
        topic: TopicRouting.Topic = TopicRouting.Topic.GENERAL
    ): List<SearchHit> {
        val intentQuestion = if (topic == TopicRouting.Topic.OFFICIAL) {
            "上海 $original 官方公众号与主流媒体发布的近期活动、展览或开放信息"
        } else {
            "上海 $original 的历史与近况"
        }
        val payload = JSONObject().apply {
            put("query", query.take(128))
            put("count", count.coerceIn(1, 50))
            put("ori_question", intentQuestion.take(128))
            put("need_content", false)
        }
        val request = Request.Builder()
            .url(DEEPSEEK_SEARCH_URL)
            .addHeader("Authorization", "Bearer ${settings.apiKey}")
            .post(payload.toString().toRequestBody(JSON_MEDIA))
            .build()

        http.newCall(request).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) return emptyList()
            val arr = JSONObject(text).optJSONArray("results") ?: return emptyList()
            return (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val url = o.text("url")
                val title = o.text("title")
                if (url.isBlank() || title.isBlank()) return@mapNotNull null
                SearchHit(
                    title = title,
                    url = url,
                    summary = o.text("summary").ifBlank { o.text("snippet") },
                    siteName = o.text("site_name").takeIf { it.isNotBlank() },
                    publishDate = o.text("date").takeIf { it.isNotBlank() }?.take(10),
                    score = o.optDouble("rerank_score", 0.0)
                )
            }.sortedByDescending { it.score }
        }
    }

    private fun zhipuSearch(settings: AppSettings, query: String, count: Int): List<SearchHit> {
        val payload = JSONObject().apply {
            put("search_engine", "search_std")
            put("search_query", query.take(70))
            put("count", count.coerceIn(1, 50))
        }
        val request = Request.Builder()
            .url(ZHIPU_SEARCH_URL)
            .addHeader("Authorization", "Bearer ${settings.apiKey}")
            .post(payload.toString().toRequestBody(JSON_MEDIA))
            .build()

        http.newCall(request).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) return emptyList()
            val arr = JSONObject(text).optJSONArray("search_result") ?: return emptyList()
            return (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val link = o.text("link")
                val title = o.text("title")
                if (link.isBlank() || title.isBlank()) return@mapNotNull null
                SearchHit(
                    title = title,
                    url = link,
                    summary = o.text("content"),
                    siteName = o.text("media").takeIf { it.isNotBlank() },
                    publishDate = o.text("publish_date").takeIf { it.isNotBlank() }?.take(10)
                )
            }
        }
    }

    /** 自定义检索端点：请求 {"query": "..."}，按常见字段名兼容解析 */
    private fun customSearch(settings: AppSettings, query: String, count: Int): List<SearchHit> {
        val payload = JSONObject().apply {
            put("query", query.take(128))
            put("count", count)
        }
        val builder = Request.Builder()
            .url(settings.customSearchUrl.trim())
            .post(payload.toString().toRequestBody(JSON_MEDIA))
        val token = settings.customSearchKey.ifBlank { settings.apiKey }.trim()
        if (token.isNotBlank()) {
            builder.addHeader("Authorization", "Bearer $token")
        }
        val request = builder.build()

        http.newCall(request).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) return emptyList()
            val root = JSONObject(text)
            val arr = root.optJSONArray("results")
                ?: root.optJSONArray("search_result")
                ?: root.optJSONArray("data")
                ?: return emptyList()
            return (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val url = o.text("url").ifBlank { o.text("link") }
                val title = o.text("title").ifBlank { o.text("name") }
                if (url.isBlank() || title.isBlank()) return@mapNotNull null
                SearchHit(
                    title = title,
                    url = url,
                    summary = o.text("summary")
                        .ifBlank { o.text("content") }
                        .ifBlank { o.text("snippet") },
                    siteName = o.text("site_name").takeIf { it.isNotBlank() },
                    publishDate = o.text("date").takeIf { it.isNotBlank() }?.take(10)
                )
            }
        }
    }
}
