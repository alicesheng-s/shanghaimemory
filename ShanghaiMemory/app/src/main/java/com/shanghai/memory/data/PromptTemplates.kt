package com.shanghai.memory.data

import com.shanghai.memory.data.model.PoiInfo
import com.shanghai.memory.data.model.SearchHit

/**
 * 提示词定稿基准来自《不一样的上海记忆-项目需求定稿.md》第 8 节。
 *
 * 相对原稿的三处调整：
 * 1. P1 原文要求「模型侧先联网检索」，主路径改为 App 侧先调官方 Search API 取资料再生成，
 *    因此采用 P1 的结构与 F4 规定的尾注，配合 P1' 的「资料为依据」约束。
 * 2. 原稿是一段长文；0.2 起改为四段固定结构（地点介绍/经典历史事件/近期新闻/近期活动），
 *    便于分段渲染与「有则显示」的筛选。
 * 3. 明确禁用 Markdown 记号。模型偶尔仍会输出 `**`、`##`，渲染层另有容错解析兜底。
 */
object PromptTemplates {

    private const val SYSTEM_STORY =
        "你是「上海城市记忆讲述者」，文风克制、具体、有现场感，只陈述你能够负责的事实。"

    private const val SYSTEM_CHAT = "你是「上海城市记忆讲述者」，正在和一个站在上海街头的人对话。\n" +
        "回答用纯文本分段，每段不超过四行；不使用 Markdown 记号（不要 **、##、---、表格）。\n" +
        "涉及年代、人名、数据而你不确定的，直接说「此事待考」，不要含糊其辞，也不要编造。"

    private const val SYSTEM_ROUTE =
        "你是上海城市漫游路线策展人，只输出符合要求的 JSON，不输出任何解释性文字。"

    private const val SYSTEM_SPOT =
        "你是熟悉上海城市史与工业遗产的向导，只输出符合要求的 JSON，不输出任何解释性文字。"

    private const val SECTION_RULE = """输出格式（严格遵守）：
段标题各占一行、用中文方括号包住，正文另起一行；全部使用纯文本，禁止 **、##、---、表格等 Markdown 记号。
【地点介绍】
【经典历史事件】
【近期新闻】
【近期活动】
四段写完后，最后单独一行输出指定的尾注，不要加别的字。"""

    private fun poiBlock(poi: PoiInfo): String =
        "【地点信息】名称：${poi.name}；类型：${poi.type}；位置：${poi.locationText.ifBlank { "上海" }}；坐标：${poi.lng},${poi.lat}"

    /** 有检索资料时的用户提示词 */
    fun storyUserPrompt(poi: PoiInfo, hits: List<SearchHit>): String {
        val sources = hits.take(6).mapIndexed { i, h -> h.toPromptBlock(i) }.joinToString("\n\n")
        return """
你是「上海城市记忆讲述者」。用户正站在上海某处，向你询问脚下地点的故事。
以下是从公开资料中检索到的该地点相关摘要，你的全部陈述必须以这些资料为依据，资料未覆盖的内容不得编写：

【检索资料】
$sources

${poiBlock(poi)}

先做一次判断：如果这个地点只是便利店、车行、连锁餐饮、普通小区或写字楼，它没有可讲述的公共历史，
不要替它编故事，也不要拿工商注册、企业年报、招投标、招聘信息来充数——那不属于城市记忆。
这种情况直接输出下面四段，一个字都不要多写：
【地点介绍】这里是一家普通的（按实际类型填写）场所，没有可讲述的公共历史。
【经典历史事件】无。
【近期新闻】无。
【近期活动】无。

写作底线（比下面的分段要求更重要）：
- 【地点介绍】只能讲这栋建筑或这条街的来历与变迁：哪一年建、当时做什么、后来怎么变、今天是什么。
- 禁止罗列机构职能、内设科室、业务范围、领导人、行政级别——那是百科条目，不是城市记忆。
- 如果连一个确切年份都给不出，说明这里没有可讲述的公共历史，按上面的格式直接说没有，不要用职能描述凑字数。

各部分要求：
1. 【地点介绍】150-250 字：这里是什么地方、从哪一年代出现、当年做什么用、今天变成什么。
2. 【经典历史事件】1-2 件，写清年份、涉及的具体人物或机构、发生了什么；资料不足处写「此事待考」，禁止编造年代、人名与数据。
3. 【近期新闻】近一两年与该地点或所在街区相关的公开报道 1-2 条，写清事件与时间；若资料中没有，整段只写「暂无可靠近况」。
4. 【近期活动】当前可参与的展览、演出、市集或开放时间等实用信息；若无，整段只写「暂无可靠活动信息」。
5. 史实与民间传说分开表述，传说必须注明「传说」。

$SECTION_RULE
尾注固定为：内容由 AI 联网检索生成，仅供参考。
        """.trimIndent()
    }

    /** 检索不可用时的纯对话降级版 */
    fun storyUserPromptFallback(poi: PoiInfo): String {
        return """
你是「上海城市记忆讲述者」。用户正站在上海某处，向你询问脚下地点的故事。

${poiBlock(poi)}

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

$SECTION_RULE
尾注固定为：内容由 AI 生成，未联网核实，仅供参考。
        """.trimIndent()
    }

    fun storySystem(): String = SYSTEM_STORY

    fun chatSystem(): String = SYSTEM_CHAT

    /** 聊天里的连续追问：把当前地点的四段底稿作为背景，避免重复检索同一件事 */
    fun followUpUserPrompt(poi: PoiInfo, draft: String, question: String): String = """
我们正在聊【${poi.name}】（${poi.locationText.ifBlank { "上海" }}）。下面是我先前为该地点整理的四段底稿，你可以引用，但不要整段复述：

${draft.take(1600)}

我的问题：$question

请直接回答，不要重新分四段；如涉及你不确定的史实，注明「此事待考」。
    """.trimIndent()

    fun routeUserPrompt(
        keywords: String,
        hours: Int?,
        notes: String = "",
        activities: String = "",
        fixedStops: List<String> = emptyList()
    ): String {
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
        // 用户在选点页亲手挑过的站点是硬约束：只准用这些，不许改动名单
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

    fun routeSystem(): String = SYSTEM_ROUTE

    /**
     * 进入策划通道时的开场白。
     * 只有确实带着预设站点进来才列站名；用户自己敲关键词时不凭空塞入预设路线。
     */
    fun planOpener(routeName: String, stops: List<String>): String {
        val head = if (stops.isEmpty()) {
            "我们从零来排「$routeName」这条路线，站点由你我一起定。"
        } else {
            "我们来把「$routeName」走成一条属于你的路线。\n\n已选站点：${stops.joinToString(" → ")}"
        }
        return head + "\n\n" +
            "先确认几件事，我好据此调整：\n" +
            "1. 你能走多久？大概几小时、能接受多少步行量？\n" +
            "2. 有没有必须看或不想看的内容？例如更想看厂房机器，还是更想听人的故事。\n" +
            "3. 同行的是自己、朋友，还是长辈或小孩？节奏会不一样。\n\n" +
            "直接说两三句就行，我按你的回答排站点和停留时间。"
    }

    /** 连通性测试用的极短提示词，只要首字延迟与鉴权状态 */
    fun pingUserPrompt(): String = "只回复两个字：在线"

    // ---- 自定义路线的地点检索 ----

    fun spotSystem(): String = SYSTEM_SPOT

    /**
     * 让模型按关键词与偏好给出候选地点。
     * 关键词支持 and / or / not 的写法，原样交给模型去理解，再要求它按布尔含义挑选。
     */
    fun spotUserPrompt(keywords: String, preference: String, want: Int = 10): String {
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
