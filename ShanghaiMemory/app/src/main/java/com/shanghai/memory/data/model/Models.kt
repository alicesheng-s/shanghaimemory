package com.shanghai.memory.data.model

/** 地图中心点辨认出的地点 */
data class PoiInfo(
    val name: String,
    val type: String,
    val address: String,
    val district: String,
    val lat: Double,
    val lng: Double,
    val amapId: String? = null,
    val distanceMeters: Int = 0
) {
    val locationText: String
        get() = listOf(district, address).filter { it.isNotBlank() }.distinct().joinToString(" · ")
}

/** 检索得到的一条公开资料 */
data class SearchHit(
    val title: String,
    val url: String,
    val summary: String,
    val siteName: String? = null,
    val publishDate: String? = null,
    val score: Double = 0.0
) {
    /** 拼入提示词的单条资料，控制长度避免挤爆上下文 */
    fun toPromptBlock(index: Int): String {
        val body = summary.take(700)
        val head = buildString {
            append("［资料 ${index + 1}］")
            append(title)
            if (!siteName.isNullOrBlank()) append("（$siteName）")
            if (!publishDate.isNullOrBlank()) append(" 发布于 $publishDate")
            append("\n来源：").append(url)
        }
        return "$head\n$body"
    }
}

/** P2 路线生成的结构化结果 */
data class RouteStop(
    val seq: Int,
    val name: String,
    val area: String,
    val stayMin: Int,
    val reason: String,
    val storyHook: String,
    val lat: Double = 0.0,
    val lng: Double = 0.0
) {
    /** 坐标是否已通过高德地理编码补齐，没补齐的站点只列文字不上图 */
    val located: Boolean get() = lat != 0.0 && lng != 0.0
}

data class RoutePlan(
    val routeName: String,
    val theme: String,
    val stops: List<RouteStop>,
    val transitionLogic: String,
    val totalMin: Int
)

/** 故事生成阶段，用于 UI 呈现「检索 → 讲述」两段 */
enum class StoryPhase {
    IDLE,
    SEARCHING,
    GENERATING,
    DONE,
    FAILED,
    OFFLINE_CURATED
}
