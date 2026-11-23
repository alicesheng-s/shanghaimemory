package com.shanghai.memory.data

/**
 * 话题决定去哪儿找信息。
 *
 * 二次元与游戏的活人讨论在 B 站、贴吧、小黑盒；美食与景色看小红书、大众点评、微博；
 * 冷门话题只剩古早论坛还留着记录。
 *
 * DeepSeek 搜索接口没有 domain 参数（body 只有 query / count / ori_question /
 * localization / need_content），但 query 支持搜索引擎运算符 site:、-site:、after:、before:、
 * lang: 与引号短语，所以这里用「(site:a OR site:b)」把检索压到目标站点。
 * query 上限 128 字符，拼的时候要按长度做裁剪。
 */
object TopicRouting {

    enum class Topic(val label: String, val sites: List<String>) {
        GAME("游戏二次元", listOf("bilibili.com", "tieba.baidu.com", "xiaoheihe.cn")),
        DIGITAL("数码", listOf("bilibili.com", "xiaoheihe.cn", "tieba.baidu.com")),
        FOOD("美食", listOf("xiaohongshu.com", "dianping.com", "weibo.com")),
        SCENERY("景色", listOf("xiaohongshu.com", "mafengwo.cn", "weibo.com")),
        NICHE("冷门", listOf("bilibili.com", "youku.com", "tieba.baidu.com", "nga.cn")),

        /** 历史建筑与城市记忆不加站点限制：公众号、地方志、新闻站到处都是，限死反而搜不到 */
        GENERAL("通用", emptyList())
    }

    private val GAME_WORDS = listOf(
        "游戏", "二次元", "动漫", "番剧", "手办", "谷子", "漫展", "同人",
        "手游", "主机", "电竞", "原神", "崩坏", "碧蓝", "明日方舟", "抽卡"
    )
    private val DIGITAL_WORDS = listOf(
        "数码", "手机", "电脑", "显卡", "耳机", "相机", "键盘", "装机", "评测", "极客"
    )
    private val FOOD_WORDS = listOf(
        "美食", "小吃", "咖啡", "餐厅", "面馆", "本帮菜", "夜市", "甜品", "探店", "早餐"
    )
    private val SCENERY_WORDS = listOf(
        "景色", "风景", "拍照", "打卡", "公园", "滨江", "外滩", "梧桐", "日落", "机位"
    )
    private val NICHE_WORDS = listOf(
        "冷门", "小众", "怀旧", "古早", "传说", "野史", "轶事", "旧事", "秘闻", "老照片"
    )

    /** 按优先级识别话题：越具体的词越先判定 */
    fun detect(vararg texts: String): Topic {
        val haystack = texts.joinToString(" ")
        return when {
            NICHE_WORDS.any { haystack.contains(it, ignoreCase = true) } -> Topic.NICHE
            GAME_WORDS.any { haystack.contains(it, ignoreCase = true) } -> Topic.GAME
            DIGITAL_WORDS.any { haystack.contains(it, ignoreCase = true) } -> Topic.DIGITAL
            FOOD_WORDS.any { haystack.contains(it, ignoreCase = true) } -> Topic.FOOD
            SCENERY_WORDS.any { haystack.contains(it, ignoreCase = true) } -> Topic.SCENERY
            else -> Topic.GENERAL
        }
    }

    /**
     * 把站点约束拼进 query，保证不超过接口的 128 字符上限。
     * 站点越多越精确，但拼太长会把原问题挤掉，所以从后往前丢站点。
     */
    fun decorate(query: String, topic: Topic): String {
        if (topic.sites.isEmpty()) return query.take(MAX_QUERY)
        val base = query.trim().take(MAX_QUERY - 32)
        val sites = topic.sites.toMutableList()
        while (sites.isNotEmpty()) {
            val clause = sites.joinToString(" OR ") { "site:$it" }
            val candidate = "$base ($clause)"
            if (candidate.length <= MAX_QUERY) return candidate
            sites.removeAt(sites.size - 1)
        }
        return base
    }

    /**
     * 「最近的活动」查询：用 after: 把时间窗压到最近八个月，
     * 免得模型拿三年前已结束的展览当近况。
     */
    fun recentActivityQuery(keywords: String, monthsBack: Long = 8): String {
        val since = java.time.LocalDate.now().minusMonths(monthsBack).toString().take(7)
        val head = "${keywords.ifBlank { "上海" }} 上海 活动 展览 演出"
        return decorate("$head after:$since", Topic.SCENERY).take(MAX_QUERY)
    }

    /** 接口限制：query 最短 1 字、最长 128 字 */
    private const val MAX_QUERY = 128
}
