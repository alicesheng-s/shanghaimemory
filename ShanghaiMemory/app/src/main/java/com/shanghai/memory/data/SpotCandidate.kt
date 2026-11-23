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
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    /** 到用户当前位置的直线距离（米）；负数表示没拿到定位或没查到坐标 */
    val distanceMeters: Float = -1f,
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
            blurb = spot.blurb
        )
    }
}
