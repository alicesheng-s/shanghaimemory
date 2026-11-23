package com.shanghai.memory

import android.app.Application
import com.amap.api.location.AMapLocationClient
import com.amap.api.maps.MapsInitializer
import com.amap.api.services.core.ServiceSettings
import com.shanghai.memory.data.SettingsStore
import com.shanghai.memory.ui.theme.Appearance
import kotlinx.coroutines.runBlocking

/**
 * 高德自 2022 年起要求先完成隐私合规声明再初始化 SDK，
 * 否则地图与检索会静默失败（只出灰底、POI 返回空）。
 *
 * 另外配色必须在任何 Activity.onCreate 之前确定，所以这里同步读一次 DataStore，
 * 否则首屏会先闪一次默认色板再被重建。
 */
class App : Application() {

    override fun onCreate() {
        super.onCreate()

        MapsInitializer.updatePrivacyShow(this, true, true)
        MapsInitializer.updatePrivacyAgree(this, true)

        ServiceSettings.updatePrivacyShow(this, true, true)
        ServiceSettings.updatePrivacyAgree(this, true)

        AMapLocationClient.updatePrivacyShow(this, true, true)
        AMapLocationClient.updatePrivacyAgree(this, true)

        runBlocking {
            Appearance.pushToState(this@App)
        }
    }
}
