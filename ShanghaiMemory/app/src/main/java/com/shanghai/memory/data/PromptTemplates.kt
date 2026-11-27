package com.shanghai.memory.data

import com.shanghai.memory.data.model.PoiInfo
import com.shanghai.memory.data.model.SearchHit
import com.shanghai.memory.ui.theme.ThemeState

/**
 * 提示词定稿基准来自《不一样的上海记忆-项目需求定稿.md》第 8 节。
 * 0.8.0 起支持中英双语讲述与策展，由 [ThemeState.isEnglish] 切换。
 */
object PromptTemplates {

    private val en: Boolean get() = ThemeState.isEnglish

    private const val SYSTEM_STORY_ZH =
        "你是「上海城市记忆讲述者」，像一位懂建筑史又好聊的老朋友，文风克制、具体、有现场感，只讲有据可查的事实。"

    private const val SYSTEM_STORY_EN =
        "You are the 'Shanghai Memory Storyteller'—a knowledgeable, warm local guide standing beside the visitor. Write vividly and concretely in English, sticking strictly to verifiable facts."

    private const val SYSTEM_CHAT_ZH = "你是「上海城市记忆讲述者」，正在和一个站在上海街头的人聊天。\n" +
        "回答用纯文本分段，每段不超过四行；不使用 Markdown 记号（不要 **、##、---、表格）。\n" +
        "涉及年代、人名、数据而你不确定的，坦诚说「此事待考」，不要含糊，也不要编造。"

    private const val SYSTEM_CHAT_EN = "You are the 'Shanghai Memory Storyteller', chatting with someone standing on a Shanghai street.\n" +
        "Reply in plain-text paragraphs (at most 4 lines each) with no Markdown syntax (no **, ##, ---, or tables).\n" +
        "If any date, name, or figure cannot be verified, state 'To be verified' honestly—never guess or invent facts."

    private const val SYSTEM_ROUTE_ZH =
        "你是上海城市漫游路线策展人，只输出符合要求的 JSON，不输出任何解释性文字。"

    private const val SYSTEM_ROUTE_EN =
        "You are a Shanghai walking-route curator. Output strictly valid JSON only, with no extra commentary."

    private const val SYSTEM_SPOT_ZH =
        "你是熟悉上海城市史与工业遗产的向导，只输出符合要求的 JSON，不输出任何解释性文字。"

    private const val SYSTEM_SPOT_EN =
        "You are a guide to Shanghai's urban and industrial heritage. Output strictly valid JSON only, with no extra commentary."

    private const val SECTION_RULE_ZH = """输出格式（严格遵守）：
段标题各占一行、用中文方括号包住，正文另起一行；全部使用纯文本，禁止 **、##、---、表格等 Markdown 记号。
【地点介绍】
【经典历史事件】
【近期新闻】
【近期活动】
四段写完后，最后单独一行输出指定的尾注，不要加别的字。"""

    private const val SECTION_RULE_EN = """Output format (follow strictly):
Each section heading must sit on its own line in square brackets, followed by body text on the next line. Use plain text only—no **, ##, ---, or tables.
[About This Place]
[Key Historical Events]
[Recent News]
[Upcoming Events]
After the four sections, end with the exact required footer line."""

    private fun poiBlock(poi: PoiInfo, parentName: String? = null): String = if (en) {
        val parentSuffix = if (!parentName.isNullOrBlank()) "; Parent Landmark: $parentName" else ""
        "[Place Info] Name: ${poi.name}$parentSuffix; Type: ${poi.type}; Area: ${poi.locationText.ifBlank { "Shanghai" }}; Coordinates: ${poi.lng},${poi.lat}"
    } else {
        val parentSuffix = if (!parentName.isNullOrBlank()) "；所属母体地标：$parentName" else ""
        "【地点信息】名称：${poi.name}$parentSuffix；类型：${poi.type}；位置：${poi.locationText.ifBlank { "上海" }}；坐标：${poi.lng},${poi.lat}"
    }

    private fun scopeNote(poi: PoiInfo, parentName: String?, expandParent: Boolean): String = when {
        expandParent && !parentName.isNullOrBlank() -> if (en) {
            "[Scope Rule] The visitor is standing at sub-spot \"${poi.name}\" inside \"$parentName\". Do NOT confine the story to this small corner alone—center [About This Place], [Key Historical Events], [Recent News], and [Upcoming Events] on \"$parentName\" and naturally connect back to \"${poi.name}\"."
        } else {
            "【讲述范围】用户当前位于「$parentName」内的子点位「${poi.name}」。切忌只盯着小草坪、浮雕墙或单个门牌写；请重点讲清「$parentName」的整体来历、代表性历史与官方近况，再自然落回脚下的「${poi.name}」。"
        }
        expandParent -> if (en) {
            "[Scope Rule] If \"${poi.name}\" sits within a larger campus, park, factory complex, or historic block (check Area), bring in that broader heritage and official updates rather than writing narrowly about a tiny spot."
        } else {
            "【讲述范围】若「${poi.name}」身处高校、园区、老厂区或历史街区之内，请结合所属整体区域与官方号资讯来写，不要被单一小地名框死。"
        }
        else -> if (en) {
            "[Scope Rule] Exact-spot mode is ON: focus strictly on \"${poi.name}\" itself without broadening into the surrounding campus or complex."
        } else {
            "【讲述范围】用户已关闭「关联更大范围地标」，请严格围绕「${poi.name}」这个精确地名本身讲述，不要扩大成周边整个学校或园区的通史。"
        }
    }

    /** 有检索资料时的用户提示词 */
    fun storyUserPrompt(
        poi: PoiInfo,
        hits: List<SearchHit>,
        parentName: String? = null,
        expandParentContext: Boolean = true
    ): String {
        val sources = hits.take(8).mapIndexed { i, h -> h.toPromptBlock(i) }.joinToString("\n\n")
        val scope = scopeNote(poi, parentName, expandParentContext)
        if (en) {
            return """
You are the "Shanghai Memory Storyteller." A visitor is standing at a spot in Shanghai and wants to hear its story in English.
Base every factual claim on the public sources below; do not invent details outside these sources:

[Sources]
$sources

${poiBlock(poi, parentName)}
$scope

First, check if this place is merely an ordinary convenience store, car dealer, chain café, regular apartment block, or commercial office with no meaningful public heritage.
If so, do NOT pad with business registration or corporate trivia. Output only these exact four lines:
[About This Place] This is an everyday commercial or residential venue with no shared public history to tell.
[Key Historical Events] None.
[Recent News] None.
[Upcoming Events] None.

Editorial rules:
- [About This Place] must tell the story of this building or street: when it began, what it did, how it changed, and what stands here today.
- Never list bureaucratic department duties or corporate org charts.
- If you cannot cite even one reliable historical year, use the refusal format above.

Sections (write in engaging, natural English):
1. [About This Place] 120-180 words: what this place is, when it emerged, its original use, and its present state.
2. [Key Historical Events] 1-2 specific events with years and people/institutions involved; mark uncertain details as "To be verified."
3. [Recent News] 1-2 public updates from the past 1-2 years if present in the sources; otherwise write only "No reliable recent updates."
4. [Upcoming Events] Current exhibitions, campus/park notices from official accounts or media, open hours, or public programs if present; otherwise write only "No confirmed upcoming events."
5. Keep verified history separate from folklore (label legends clearly as "Legend").

$SECTION_RULE_EN
Footer line: Generated by AI with web search; for reference only.
            """.trimIndent()
        }
        return """
你是「上海城市记忆讲述者」。用户正站在上海某处，向你询问脚下地点的故事。
以下是从公开资料、官方公众号与主流媒体检索到的相关摘要，你的全部陈述必须以这些资料为依据，资料未覆盖的内容不得编写：

【检索资料】
$sources

${poiBlock(poi, parentName)}
$scope

先做一次判断：如果这个地点只是便利店、车行、连锁餐饮、普通小区或写字楼，它没有可讲述的公共历史，
不要替它编故事，也不要拿工商注册、企业年报、招投标、招聘信息来充数——那不属于城市记忆。
这种情况直接输出下面四段，一个字都不要多写：
【地点介绍】这里是一家普通的（按实际类型填写）场所，没有可讲述的公共历史。
【经典历史事件】无。
【近期新闻】无。
【近期活动】无。

写作底线（比下面的分段要求更重要）：
- 【地点介绍】只能讲这栋建筑或这条街（或所属母体地标）的来历与变迁：哪一年建、当时做什么、后来怎么变、今天是什么。
- 禁止罗列机构职能、内设科室、业务范围、领导人、行政级别——那是百科条目，不是城市记忆。
- 如果连一个确切年份都给不出，说明这里没有可讲述的公共历史，按上面的格式直接说没有，不要用职能描述凑字数。

各部分要求：
1. 【地点介绍】150-250 字：这里是什么地方、从哪一年代出现、当年做什么用、今天变成什么。
2. 【经典历史事件】1-2 件，写清年份、涉及的具体人物或机构、发生了什么；资料不足处写「此事待考」，禁止编造年代、人名与数据。
3. 【近期新闻】近一两年与该地点（或所属母体地标）相关的公开报道 1-2 条，写清事件与时间；若资料中没有，整段只写「暂无可靠近况」。
4. 【近期活动】优先提取官方公众号、高校/场馆公告与官媒发布的展览、讲座、演出、市集或开放时间等实用信息；若无，整段只写「暂无可靠活动信息」。
5. 史实与民间传说分开表述，传说必须注明「传说」。

$SECTION_RULE_ZH
尾注固定为：内容由 AI 联网检索生成，仅供参考。
        """.trimIndent()
    }

    /** 检索不可用时的纯对话降级版 */
    fun storyUserPromptFallback(
        poi: PoiInfo,
        parentName: String? = null,
        expandParentContext: Boolean = true
    ): String {
        val scope = scopeNote(poi, parentName, expandParentContext)
        if (en) {
            return """
You are the "Shanghai Memory Storyteller." A visitor is standing at a spot in Shanghai and wants to hear its story in English.

${poiBlock(poi, parentName)}
$scope

First, if this place is merely an everyday convenience store, car shop, chain eatery, or regular residential/office building with no public history, output only:
[About This Place] This is an everyday commercial or residential venue with no shared public history to tell.
[Key Historical Events] None.
[Recent News] None.
[Upcoming Events] None.

Editorial rules:
- Focus on the building or street's origin, transformation, and present look.
- Never list bureaucratic duties or corporate org charts.
- If you cannot give even one solid year, use the refusal lines above.

Sections (write in warm, clear English):
1. [About This Place] 120-180 words on its origins, past role, and present look.
2. [Key Historical Events] 1-2 dated events with concrete people or institutions; write "To be verified" for anything uncertain.
3. [Recent News] Write only "Offline mode: recent news cannot be verified."
4. [Upcoming Events] Write only "Offline mode: upcoming events cannot be verified."
5. Separate verified facts from folklore (mark legends clearly as "Legend").

$SECTION_RULE_EN
Footer line: Generated by AI without web verification; for reference only.
            """.trimIndent()
        }
        return """
你是「上海城市记忆讲述者」。用户正站在上海某处，向你询问脚下地点的故事。

${poiBlock(poi, parentName)}
$scope

先做一次判断：如果这个地点只是便利店、车行、连锁餐饮、普通小区或写字楼，它没有可讲述的公共历史，
不要替它编故事，直接输出下面四段，一个字都不要多写：
【地点介绍】这里是一家普通的（按实际类型填写）场所，没有可讲述的公共历史。
【经典历史事件】无。
【近期新闻】无。
【近期活动】无。

写作底线（比下面的分段要求更重要）：
- 【地点介绍】只能讲这栋建筑或这条街的来历与变迁：哪一年建、当时做什么、后来怎么变、今天是什么。
- 禁止罗列机构职能、内设科室、业务范围、领导人、行政级别——那是百科条目，不是城市记忆。
- 如果连一个确切年份都给不出，说明这里没有可讲述的公共历史，按上面的格式直接说没有，不要用职能描述凑字数。

各部分要求：
1. 【地点介绍】150-250 字：这里是什么地方、大致从哪个年代出现、当年做什么用、今天变成什么。
2. 【经典历史事件】1-2 件，写清年份、涉及的具体人物或机构；任何无法确认的年代、人名、数据一律写「此事待考」，禁止编造。
3. 【近期新闻】你没有联网，若无法确认，整段只写「未联网，暂无法确认近况」。
4. 【近期活动】同上，整段只写「未联网，暂无法确认活动信息」。
5. 史实与民间传说分开表述，传说必须注明「传说」；若对该地历史并不掌握，如实说明后转写所在街区的地理与市井面貌，不要虚构历史。

$SECTION_RULE_ZH
尾注固定为：内容由 AI 生成，未联网核实，仅供参考。
        """.trimIndent()
    }

    fun storySystem(): String = if (en) SYSTEM_STORY_EN else SYSTEM_STORY_ZH

    fun chatSystem(): String = if (en) SYSTEM_CHAT_EN else SYSTEM_CHAT_ZH

    /** 聊天里的连续追问：把当前地点的四段底稿作为背景，避免重复检索同一件事 */
    fun followUpUserPrompt(poi: PoiInfo, draft: String, question: String): String = if (en) {
        """
We are talking about [${poi.name}] (${poi.locationText.ifBlank { "Shanghai" }}). Here are the earlier notes prepared for this spot (reference without repeating verbatim):

${draft.take(1600)}

Visitor's question: $question

Answer directly in natural English without re-splitting into four sections. Mark uncertain facts as "To be verified."
        """.trimIndent()
    } else {
        """
我们正在聊【${poi.name}】（${poi.locationText.ifBlank { "上海" }}）。下面是我先前为该地点整理的四段底稿，你可以引用，但不要整段复述：

${draft.take(1600)}

我的问题：$question

请直接回答，不要重新分四段；如涉及你不确定的史实，注明「此事待考」。
        """.trimIndent()
    }

    fun routeUserPrompt(
        keywords: String,
        hours: Int?,
        notes: String = "",
        activities: String = "",
        fixedStops: List<String> = emptyList()
    ): String {
        if (en) {
            val duration = hours?.let { "[Target Duration] $it hours" } ?: "[Target Duration] Flexible (plan around 4 hours)"
            val extra = notes.trim().ifBlank { "None" }
            val activitySection = if (activities.isBlank()) {
                "[Recent Events] None found online. Do not invent specific exhibition names or dates."
            } else {
                "[Recent Events] (Only reference verified events listed below):\n$activities"
            }
            val fixedSection = if (fixedStops.isEmpty()) "" else {
                "[Mandatory Stops] Use ONLY these exact stops (do not add, drop, or replace):\n" +
                    fixedStops.mapIndexed { i, s -> "${i + 1}. $s" }.joinToString("\n")
            }
            val stopRule = if (fixedStops.isEmpty()) {
                "1. Pick 4-6 real, walkable/metro-accessible Shanghai landmarks closely matching the theme."
            } else {
                "1. stops must contain the exact ${fixedStops.size} mandatory stops above (reorder and set stay_min based on notes)."
            }
            return """
You are a Shanghai walking-route curator. Design a realistic walking/transit route in English.

[Keywords] $keywords
$duration
[Visitor Notes] $extra

$fixedSection

$activitySection

Requirements:
$stopRule
2. Keep transit between consecutive stops under 40 minutes and cluster geographically where sensible.
3. Output ONLY this compact single-line JSON (include official Chinese landmark name in search_key for map pin lookup):
   {"route_name":"Route Title","theme":"Theme","stops":[{"seq":1,"name":"English Stop Name","search_key":"中文地标名","area":"District","stay_min":30,"reason":"Short reason","story_hook":"One-line memory hook"}],"transition_logic":"How to connect the stops","total_min":0}
4. Respect length limits so JSON won't truncate: reason <= 22 words, story_hook <= 18 words, transition_logic <= 45 words.
5. Never invent non-existent places.
            """.trimIndent()
        }

        val duration = hours?.let { "【预计游览时长】${it} 小时" } ?: "【预计游览时长】未指定，按 4 小时安排"
        val extra = notes.trim().ifBlank { "无" }
        val activitySection = if (activities.isBlank()) {
            """
【近期活动资料】没有检索到（未联网或检索无结果）。
排站点时不要提任何具体的展览名称、日期与场次，需要提及时只写「近期没有查到确定的活动」。
            """.trimIndent()
        } else {
            """
【近期活动资料】（下面是刚检索到的公开资料，只认这里出现的活动）
$activities

把仍在进行或临近的活动编进对应站点，写清活动名与时间，并标明来自哪条资料；资料里没有的活动一律不许写。
            """.trimIndent()
        }
        val fixedSection = if (fixedStops.isEmpty()) {
            ""
        } else {
            """
【必须使用的站点】用户已经亲手选定，只准用下面这几个，站名照抄，不许新增、不许替换、不许删减：
${fixedStops.mapIndexed { i, s -> "${i + 1}. $s" }.joinToString("\n")}
这些站点之外的任何地点一律不许写进 stops；顺序与停留时长可以按备注调整。
            """.trimIndent()
        }
        val stopRule = if (fixedStops.isEmpty()) {
            "1. 站点必须真实存在、可在地图定位导航，与关键词强相关，共 4-6 站。"
        } else {
            "1. stops 里必须正好是上面【必须使用的站点】列出的 ${fixedStops.size} 个地点，一个不多一个不少，顺序可调。"
        }
        return """
你是上海城市漫游路线策展人。根据用户关键词与备注设计一条可实际步行的游览路线。

【用户关键词】$keywords
$duration
【用户备注】$extra

$fixedSection

$activitySection

要求：
$stopRule
2. 站点间步行或地铁可达，单段衔接不超过 40 分钟，整体在同一片区就近聚合。
3. 严格按以下 JSON 结构输出，只输出这一个 JSON 对象，不要代码块、不要任何解释、不要换行缩进：
   {"route_name":"路线名","theme":"主题","stops":[{"seq":1,"name":"站名","area":"所在区或街区","stay_min":30,"reason":"到访理由","story_hook":"现场切口"}],"transition_logic":"衔接说明","total_min":0}
4. 字数上限（超了会被截断导致解析失败）：reason 不超过 30 字，story_hook 不超过 24 字，transition_logic 不超过 60 字。story_hook 是现场展开讲述的记忆切口，一句话即可。
5. 无法确认某站点仍存在或开放时，替换为可确认的邻近地点，禁止虚构站点（若已给出【必须使用的站点】则严格保留原名单）。
6. 用户备注里提到的体力、时间、偏好必须体现在站点选择与停留时长上。
        """.trimIndent()
    }

    fun routeSystem(): String = if (en) SYSTEM_ROUTE_EN else SYSTEM_ROUTE_ZH

    /**
     * 进入策划通道时的开场白。
     */
    fun planOpener(routeName: String, stops: List<String>): String {
        if (en) {
            val head = if (stops.isEmpty()) {
                "Let's design \"$routeName\" together from scratch."
            } else {
                "Let's shape \"$routeName\" around your pace.\n\nSelected stops: ${stops.joinToString(" → ")}"
            }
            return head + "\n\n" +
                "Before I arrange the order and timing, tell me a little about:\n" +
                "1. How long would you like to walk today?\n" +
                "2. What draws you most—old industrial machinery, architecture, or human stories?\n" +
                "3. Who's walking with you (solo, friends, elders, or kids)?\n\n" +
                "Just reply in a sentence or two and I'll build your route."
        }
        val head = if (stops.isEmpty()) {
            "我们从零开始排「$routeName」这条路线，去哪几处由你我一起定。"
        } else {
            "我们来把「$routeName」排成一趟顺路又好逛的行程。\n\n已选定站点：${stops.joinToString(" → ")}"
        }
        return head + "\n\n" +
            "动手排顺序之前，先告诉我三件小事：\n" +
            "1. 今天大概想逛几个小时，能接受多少步行量？\n" +
            "2. 更想看老机器老厂房，还是想听人情往事？\n" +
            "3. 是自己一个人逛，还是带朋友、长辈或小朋友？\n\n" +
            "随便回两三句话就行，我马上按你的节奏把路线理出来。"
    }

    /** 连通性测试用的极短提示词，只要首字延迟与鉴权状态 */
    fun pingUserPrompt(): String = if (en) "Reply with one word only: Online" else "只回复两个字：在线"

    // ---- 自定义路线的地点检索 ----

    fun spotSystem(): String = if (en) SYSTEM_SPOT_EN else SYSTEM_SPOT_ZH

    fun spotUserPrompt(keywords: String, preference: String, want: Int = 10): String {
        if (en) {
            val focus = when (preference) {
                "活动" -> "Prioritize places currently open with active exhibitions, markets, or events, and mention what's happening in blurb."
                "历史建筑" -> "Prioritize historic buildings, old mills, and warehouses where the original character remains visible."
                else -> "Balance historic architecture with lively present-day spaces."
            }
            return """The visitor wants to explore places in Shanghai matching: $keywords
Keywords may include boolean operators (and, or, not); interpret them logically rather than as literal place names.
$focus
Return $want concrete places in Shanghai:
1. Every place must genuinely exist in Shanghai and be findable on maps.
2. Make sure they are distinct places (not two buildings inside the same park).
3. Write blurb in English (under 25 words) explaining what it used to be and what it is today.
4. Put the official Chinese landmark name in "search_key" so the local map can pin it accurately.
5. Output ONLY this JSON starting with { and ending with }, no code fences or extra text:
{"spots":[{"name":"English Name","search_key":"中文地标名","area":"District + Street","blurb":"One-line intro"}]}"""
        }
        val focus = when (preference) {
            "活动" -> "优先给近一两年仍在开放、正在办展或有活动的场所，并在 blurb 里写清它现在在做什么。"
            "历史建筑" -> "优先给本身是有年头、能看出建筑年代与原来用途的老房子、老厂、老仓库。"
            else -> "老建筑与当下正在发生的事各占一半。"
        }
        return """用户想在上海找这样一类地方：$keywords
关键词可能用了 and、or、not 这类逻辑写法，按它们的布尔含义理解，不要当成地名的一部分。
$focus
请给出 $want 个具体地点，要求：
1. 必须真实存在于上海，名称要能在地图应用里直接搜到；不确定是否存在的地点一律不要写。
2. 彼此不要是同一个地方，也不要只是同一个园区里的两栋楼。
3. blurb 一句话说清它原来是什么、现在是什么，不超过 45 字。
4. 只输出下面这个 JSON，第一个字符是 {，最后一个字符是 }，不要代码块、不要解释、不要换行缩进：
{"spots":[{"name":"地点名","area":"区+路名或门牌","blurb":"一句话介绍"}]}"""
    }
}
