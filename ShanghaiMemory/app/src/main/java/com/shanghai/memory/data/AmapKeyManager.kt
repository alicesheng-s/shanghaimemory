package com.shanghai.memory.data

import android.content.Context
import android.content.pm.PackageManager
import com.amap.api.location.AMapLocationClient
import com.amap.api.maps.MapsInitializer
import com.amap.api.services.core.ServiceSettings

/**
 * 运行时切换高德地图 Android API Key：
 * 用户未在设置里填写自定义 Key 时，自动回落到 AndroidManifest 里打包的内置 Key，
 * 确保清空后依然可用；同时同步应用到 3D 地图、地理/周边搜索与定位三个 SDK 模块。
 */
object AmapKeyManager {

    private const val META_AMAP_KEY = "com.amap.api.v2.apikey"

    fun apply(context: Context, customKey: String) {
        val effective = customKey.trim().ifBlank { manifestKey(context) }
        if (effective.isBlank()) return
        runCatching { MapsInitializer.setApiKey(effective) }
        runCatching { ServiceSettings.getInstance().setApiKey(effective) }
        runCatching { AMapLocationClient.setApiKey(effective) }
    }

    fun manifestKey(context: Context): String = runCatching {
        val info = context.packageManager.getApplicationInfo(
            context.packageName,
            PackageManager.GET_META_DATA
        )
        info.metaData?.getString(META_AMAP_KEY).orEmpty().trim()
    }.getOrDefault("")
}
