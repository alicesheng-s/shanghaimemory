package com.shanghai.memory.data

import android.location.Location

/**
 * 候选地点：预设库里的一条，或自定义检索回来的一条。
 * 坐标与距离是运行时补的 —— 名字先定，位置后查。
 */
data class SpotCandidate(
    val name: String,
    val area: String,
    val blurb: String,
    /** 地理编码时优先用的检索词：策展库里为重名地点准备的限定写法，空则用 [name] */
    val searchKey: String = "",
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    /** 到用户当前位置的直线距离（米）；负数表示没拿到定位或没查到坐标 */
    val distanceMeters: Float = -1f,
    /**
     * 已经尝试过地理编码。为 true 且 [located] 仍为 false，说明这一站查不到坐标，
     * 界面要显示「未能定位」而不是永远挂着「正在定位…」。
     */
    val locateAttempted: Boolean = false,
) {
    val located: Boolean get() = lat != 0.0 && lng != 0.0

    val distanceText: String
        get() = when {
            distanceMeters < 0f -> ""
            distanceMeters < 950f -> "${distanceMeters.toInt()} 米"
            else -> "%.1f 公里".format(distanceMeters / 1000f)
        }

    fun withDistance(fromLat: Double, fromLng: Double): SpotCandidate {
        if (!located) return this
        val result = FloatArray(1)
        Location.distanceBetween(fromLat, fromLng, lat, lng, result)
        return copy(distanceMeters = result[0])
    }

    fun locatedAt(lat: Double, lng: Double): SpotCandidate = copy(lat = lat, lng = lng)

    companion object {
        fun of(spot: CuratedSpot) = SpotCandidate(
            name = spot.name,
            area = spot.area,
            blurb = spot.blurb,
            searchKey = spot.searchKey
        )
    }
}
