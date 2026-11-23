package com.shanghai.memory.data

/**
 * 进程内记一份最近一次定位。
 *
 * 地图页本来就一直在定位，路线页没必要再开一个客户端去抢；
 * 地图还没定位过（比如直接从侧栏进来）时，路线页再自己单次定位一次补上。
 */
object LocationHolder {

    @Volatile
    var lat: Double = 0.0
        private set

    @Volatile
    var lng: Double = 0.0
        private set

    val hasFix: Boolean get() = lat != 0.0 && lng != 0.0

    fun update(lat: Double, lng: Double) {
        this.lat = lat
        this.lng = lng
    }
}
