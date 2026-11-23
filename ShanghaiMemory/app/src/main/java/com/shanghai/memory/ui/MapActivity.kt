package com.shanghai.memory.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.amap.api.location.AMapLocationClient
import com.amap.api.location.AMapLocationClientOption
import com.amap.api.maps.AMap
import com.amap.api.maps.CameraUpdateFactory
import com.amap.api.maps.model.CameraPosition
import com.amap.api.maps.model.LatLng
import com.amap.api.maps.model.LatLngBounds
import com.amap.api.maps.model.Marker
import com.amap.api.maps.model.MarkerOptions
import com.amap.api.maps.model.Polyline
import com.amap.api.maps.model.PolylineOptions
import com.shanghai.memory.R
import com.shanghai.memory.data.AmapService
import com.shanghai.memory.data.GatePolicy
import com.shanghai.memory.data.LandmarkGate
import com.shanghai.memory.data.LocationHolder
import com.shanghai.memory.data.RouteSession
import com.shanghai.memory.data.SettingsStore
import com.shanghai.memory.data.model.PoiInfo
import com.shanghai.memory.data.model.RoutePlan
import com.shanghai.memory.databinding.ActivityMapBinding
import com.shanghai.memory.ui.theme.ThemeState
import com.shanghai.memory.ui.theme.ThemedActivity
import com.shanghai.memory.ui.theme.colorOf
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * F1 地图漫游：游标固定在屏幕中心，地图在下面移动，停下即辨认脚下地点。
 * 同时承载路线向导：路线画在图上，定位跟着走，走近下一站就提示到达。
 */
class MapActivity : ThemedActivity() {

    private companion object {
        // 外滩，作为首次打开展示的默认视角
        const val DEFAULT_LAT = 31.2397
        const val DEFAULT_LNG = 121.4900
        const val DEFAULT_ZOOM = 16f
        const val USER_ZOOM = 17f
        const val SEARCH_ZOOM = 17f
        const val DEBOUNCE_MS = 400L
        const val ARRIVE_METERS = 80f
        const val REQ_LOCATION = 1001
        const val DRAWER_WIDTH_DP = 292

        /** 这个距离以内的 POI 才算「脚下就是它」，更远的只当参考地标 */
        const val NEAR_METERS = 120f

        /** 由近到远逐级放宽：先看脚下，找不到再看周边 */
        val SEARCH_RADII = intArrayOf(120, 400, 900)

        /** 这些地方离游标再近也不值得讲述，选点时跳过 */
        val NOISE_WORDS = listOf(
            "便利店", "超市", "加油站", "停车场", "公共厕所", "卫生间",
            "ATM", "充电站", "快递", "菜鸟驿站"
        )
    }

    private lateinit var binding: ActivityMapBinding
    private lateinit var aMap: AMap
    private lateinit var amapService: AmapService

    private var resolveJob: Job? = null
    private var currentPoi: PoiInfo? = null
    private var lastResolvedKey: String? = null
    /** 游标附近的全部候选，第一个是默认选中的那个 */
    private var candidates: List<PoiInfo> = emptyList()
    /** 当前生效的地标筛选策略，设置页改完回来会重新加载 */
    private var policy: GatePolicy = GatePolicy()
    private val store: SettingsStore by lazy { SettingsStore(this) }
    /** 每次辨认递增；结果落地前对不上就丢弃，避免旧请求覆盖新位置 */
    private var requestSeq = 0

    private var locationClient: AMapLocationClient? = null
    private var myLatLng: LatLng? = null

    private val routeMarkers = mutableListOf<Marker>()
    private var routeLine: Polyline? = null

    private var drawerOpen = false
    /** 首次拿到定位后把相机挪到用户脚下，只做一次 */
    private var movedToUser = false

    /** 地图要铺满整屏，所以不让基类给根布局整体加边距，改为逐个部件避让 */
    override val autoInsetPadding: Boolean get() = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMapBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.mapView.onCreate(savedInstanceState)
        aMap = binding.mapView.map
        amapService = AmapService(this)

        setUpInsets()
        setUpMap()
        setUpActions()
        observeRoute()
        setUpDrawer()
        loadPolicy()
        maybeShowOnboarding()
    }

    /** 策略从设置里读；改了策略就让当前结论作废，重新认一次 */

    /** 第一次打开时拉起引导；引导完成后不再打扰，可从设置的一级菜单重看。 */
    private fun maybeShowOnboarding() {
        lifecycleScope.launch {
            if (store.current().onboarded) return@launch
            startActivity(OnboardingActivity.intent(this@MapActivity))
        }
    }
    private fun loadPolicy() {
        lifecycleScope.launch {
            val fresh = GatePolicy.of(store.current())
            if (fresh == policy) return@launch
            policy = fresh
            lastResolvedKey = null
            val target = aMap.cameraPosition?.target ?: LatLng(DEFAULT_LAT, DEFAULT_LNG)
            resolveAt(target.latitude, target.longitude)
        }
    }

    // ---- 系统栏避让 ----

    private fun setUpInsets() {
        // 顶部条向下让出状态栏，但背景仍然铺到状态栏后面，观感是一体的
        padForSystemBars(binding.topBar, bottom = false)
        // 右侧圆形按钮组跟着顶部条一起下移
        padForSystemBars(binding.actionBar, left = false, right = false, bottom = false)
        // 底部提示卡与侧栏让出导航条
        // 卡片必须靠 margin 上移：padding 只把内容往里挤，背景仍然贴着屏幕底边，
        // 三键导航或手势条会盖住下半个圆角，观感上是「只有上面是圆的」
        liftAboveNavigationBar(binding.bottomCard)
        liftAboveNavigationBar(binding.routePanel)
        padForSystemBars(binding.drawerPanel)
    }

    /** 在原有 margin 之上再叠一层系统栏高度，让卡片整体浮到导航条上方 */
    private fun liftAboveNavigationBar(view: View) {
        val base = (view.layoutParams as? ViewGroup.MarginLayoutParams)?.bottomMargin ?: 0
        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            val lp = v.layoutParams as ViewGroup.MarginLayoutParams
            lp.bottomMargin = base + maxOf(bars.bottom, ime.bottom)
            v.layoutParams = lp
            insets
        }
        ViewCompat.requestApplyInsets(view)
    }

    // ---- 地图 ----

    private fun setUpMap() {
        aMap.uiSettings.apply {
            isZoomControlsEnabled = false
            isScaleControlsEnabled = true
            isMyLocationButtonEnabled = false
        }
        aMap.moveCamera(
            CameraUpdateFactory.newLatLngZoom(LatLng(DEFAULT_LAT, DEFAULT_LNG), DEFAULT_ZOOM)
        )
        aMap.setOnCameraChangeListener(object : AMap.OnCameraChangeListener {
            override fun onCameraChange(position: CameraPosition) = Unit

            override fun onCameraChangeFinish(position: CameraPosition) {
                resolveAt(position.target.latitude, position.target.longitude)
            }
        })
        // 首屏不移动地图时不会触发相机回调，这里主动查一次；等定位已经到位就交给定位回调
        aMap.setOnMapLoadedListener {
            if (currentPoi == null && myLatLng == null) resolveAt(DEFAULT_LAT, DEFAULT_LNG)
        }
    }

    private fun setUpActions() {
        binding.btnStory.setOnClickListener {
            val poi = currentPoi
            if (poi == null) {
                Toast.makeText(this, R.string.map_nothing_here, Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            startActivity(ChatActivity.storyIntent(this, poi))
        }
        binding.btnSearch.setOnClickListener { showSearchDialog() }
        binding.btnChat.setOnClickListener {
            startActivity(ChatActivity.open(this))
        }
        binding.btnRoute.setOnClickListener {
            startActivity(Intent(this, RouteActivity::class.java))
        }
        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        binding.btnRouteExit.setOnClickListener { endRoute() }
    }

    // ---- F1 辨认脚下地点 ----

    /**
     * 辨认游标下地点。
     *
     * 两处曾导致「地点和地图上显示的不一致」：
     * 一是原来只在高德 POI 里挑最近的一个（搜索半径 900 米、分类还漏了「地名地址」），
     * 于是游标停在一栋老楼前，返回的却是五百米外的商场；
     * 二是 `runCatching` 会把协程取消异常一并吞掉，被取消的旧请求照旧写回界面，
     * 快速拖动后显示的就成了上一个位置的结果。
     * 现在改为：逆地理定中心点本身，POI 只在 120 米内才算「脚下」，并比对请求序号。
     */
    private fun resolveAt(lat: Double, lng: Double) {
        val key = "%.4f,%.4f".format(lat, lng)
        if (key == lastResolvedKey) return

        val seq = ++requestSeq
        resolveJob?.cancel()
        resolveJob = lifecycleScope.launch {
            binding.txtStatus.setText(R.string.map_searching)
            // 相机回调已带停止语义，这里只做极短缓冲，避免连发
            delay(DEBOUNCE_MS)

            val (district, formatted) = try {
                amapService.regeocode(lat, lng)
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                "" to ""
            }
            val scan = try {
                collectCandidates(lat, lng)
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                CandidateScan(emptyList(), null)
            }

            // 期间又拖过一次，这次结果作废
            if (seq != requestSeq) return@launch

            // 同一坐标常常挂着好几个 POI（一栋楼既是医院，也是某公司注册地），
            // 全部留下来交给用户挑，别替他猜
            candidates = scan.passed.map { poi ->
                poi.copy(
                    district = poi.district.ifBlank { district },
                    address = poi.address.ifBlank { formatted }
                )
            }

            val place = candidates.firstOrNull()
            if (place == null) {
                lastResolvedKey = null
                currentPoi = null
                candidates = emptyList()
                binding.txtPoiName.setText(R.string.map_no_landmark)
                // 只说「没有地标」用户会以为软件坏了，这里告诉他拦在哪一条上
                binding.txtPoiMeta.text = scan.rejected
                    ?.let { LandmarkGate.refusalReason(it, policy) }
                    ?: formatted.take(40)
                binding.txtStatus.setText(R.string.map_move_hint)
                renderCandidates()
                return@launch
            }

            lastResolvedKey = key
            applyPoi(place)
        }
    }

    /** 把附近够格的候选一次捞齐：显著性优先，距离次之 */
    private suspend fun collectCandidates(lat: Double, lng: Double): CandidateScan {
        val pool = mutableListOf<PoiInfo>()
        var nearestRejected: PoiInfo? = null
        for (radius in SEARCH_RADII) {
            val hits = amapService.nearby(lat, lng, radius)
            for (poi in hits) {
                // 讲不出故事的不算数，也不回退：宁可如实说这里没有，也别拉一家车行去编历史
                val ok = LandmarkGate.isWorthTelling(poi, policy) &&
                    NOISE_WORDS.none { poi.name.contains(it) }
                if (ok) {
                    pool += poi
                } else if (nearestRejected == null) {
                    // 只留一个最近被拒的，用来在卡片上解释「为什么这里没有」
                    nearestRejected = poi
                }
            }
            if (pool.size >= 3) break
        }
        val passed = pool
            .distinctBy { it.name }
            .sortedWith(compareBy({ LandmarkGate.priority(it) }, { it.distanceMeters }))
            .take(8)
        return CandidateScan(passed, if (passed.isEmpty()) nearestRejected else null)
    }

    /** 一次识别的结果：通过的候选，以及一条都没通过时最近的那条被拒记录 */
    private data class CandidateScan(val passed: List<PoiInfo>, val rejected: PoiInfo?)

    // ---- 地点搜索 ----

    /** 用户心里已经有目标时，不必靠拖地图去凑；直接按名字找 */
    private fun showSearchDialog() {
        val pad = (20 * resources.displayMetrics.density).toInt()
        val input = EditText(this).apply {
            hint = getString(R.string.search_hint)
            setSingleLine()
            setTextColor(colorOf(R.attr.shText))
            setHintTextColor(colorOf(R.attr.shTextMuted))
        }
        // setView 的带边距重载已弃用，用一个带 padding 的容器包住输入框
        val box = android.widget.FrameLayout(this).apply {
            setPadding(pad, pad / 2, pad, 0)
            addView(input)
        }
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.search_title)
            .setView(box)
            .setNegativeButton(R.string.search_cancel, null)
            .setPositiveButton(R.string.search_go, null)
            .create()
        dialog.setOnShowListener {
            // 覆写按钮点击：空输入时给提示而不是把对话框关掉
            dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE)
                .setOnClickListener {
                    val keyword = input.text.toString().trim()
                    if (keyword.isEmpty()) {
                        input.error = getString(R.string.search_need_text)
                        return@setOnClickListener
                    }
                    dialog.dismiss()
                    doSearch(keyword)
                }
        }
        dialog.show()
    }

    private fun doSearch(keyword: String) {
        resolveJob?.cancel()
        binding.txtStatus.text = getString(R.string.search_running, keyword)
        resolveJob = lifecycleScope.launch {
            val hits = try {
                amapService.searchByKeyword(keyword)
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                emptyList()
            }
            if (hits.isEmpty()) {
                binding.txtStatus.text = getString(R.string.search_empty, keyword)
                return@launch
            }
            // 搜到的可能是车行与写字楼：先按当前筛选策略过一遍，全被拒时仍然让用户自己挑
            val usable = hits.filter { LandmarkGate.isWorthTelling(it, policy) }
            val shown = usable.ifEmpty { hits }
            if (shown.size == 1) {
                focusOn(shown.first())
                return@launch
            }
            MaterialAlertDialogBuilder(this@MapActivity)
                .setTitle(getString(R.string.search_results, shown.size))
                .setItems(
                    shown.map {
                        val meta = LandmarkGate.friendlyType(it.type)
                        val where = it.address.ifBlank { it.district }
                        "${it.name}\n$meta · $where"
                    }.toTypedArray()
                ) { _, which -> focusOn(shown[which]) }
                .setNegativeButton(R.string.search_cancel, null)
                .show()
        }
    }

    /** 把游标搬到搜到的地点；顺手记住这次结论，免得相机回调立刻用周边检索把它覆盖掉 */
    private fun focusOn(poi: PoiInfo) {
        candidates = listOf(poi)
        applyPoi(poi)
        lastResolvedKey = "%.4f,%.4f".format(poi.lat, poi.lng)
        aMap.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(poi.lat, poi.lng), SEARCH_ZOOM))
    }

    private fun applyPoi(poi: PoiInfo) {
        currentPoi = poi
        binding.txtPoiName.text = poi.name
        binding.txtPoiMeta.text = buildMeta(poi)
        binding.txtStatus.text = if (poi.distanceMeters <= NEAR_METERS) {
            "此处已辨认：${poi.name}"
        } else {
            "游标附近的地标：${poi.name} · 约 ${poi.distanceMeters} 米"
        }
        renderCandidates()
    }

    /** 同一个位置往往不止一个 POI：把剩下的列出来，用户点一下就能换 */
    private fun renderCandidates() {
        val rest = candidates.size - 1
        if (rest <= 0) {
            binding.txtAlt.visibility = View.GONE
            binding.txtAlt.setOnClickListener(null)
            return
        }
        binding.txtAlt.visibility = View.VISIBLE
        binding.txtAlt.text = getString(R.string.map_alt_hint, rest)
        binding.txtAlt.setOnClickListener { showCandidatePicker() }
    }

    private fun showCandidatePicker() {
        val items = candidates.map { poi ->
            val type = LandmarkGate.friendlyType(poi.type).ifBlank { "地点" }
            "${poi.name}\n$type · 约 ${poi.distanceMeters} 米"
        }.toTypedArray()
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.map_alt_title)
            .setItems(items) { _, which ->
                candidates.getOrNull(which)?.let { applyPoi(it) }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun buildMeta(poi: PoiInfo): String {
        val parts = mutableListOf<String>()
        val type = LandmarkGate.friendlyType(poi.type)
        if (type.isNotBlank()) parts += type
        if (poi.district.isNotBlank()) parts += poi.district
        if (poi.address.isNotBlank()) parts += poi.address.take(30)
        return parts.joinToString(" · ")
    }

    // ---- 侧栏 ----

    private fun setUpDrawer() {
        binding.btnMenu.setOnClickListener { toggleDrawer(!drawerOpen) }
        binding.drawerScrim.setOnClickListener { toggleDrawer(false) }
        binding.btnDrawerChat.setOnClickListener {
            toggleDrawer(false)
            startActivity(ChatActivity.open(this))
        }
        binding.btnDrawerRoute.setOnClickListener {
            toggleDrawer(false)
            startActivity(Intent(this, RouteActivity::class.java))
        }
        binding.btnDrawerSettings.setOnClickListener {
            toggleDrawer(false)
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        binding.btnDrawerClearRoute.setOnClickListener {
            toggleDrawer(false)
            endRoute()
        }
        binding.txtDrawerPalette.text = "${ThemeState.palette.label}配色 · ${ThemeState.palette.blurb} · ${if (ThemeState.light) "亮色" else "暗色"}"
    }

    private fun toggleDrawer(open: Boolean) {
        if (drawerOpen == open) return
        drawerOpen = open
        val panel = binding.drawerPanel
        val scrim = binding.drawerScrim
        val width = DRAWER_WIDTH_DP * resources.displayMetrics.density
        if (open) {
            panel.visibility = View.VISIBLE
            scrim.visibility = View.VISIBLE
            panel.translationX = -width
            scrim.alpha = 0f
            panel.animate().translationX(0f).setDuration(180).start()
            scrim.animate().alpha(1f).setDuration(180).start()
        } else {
            panel.animate().translationX(-width).setDuration(160)
                .withEndAction { panel.visibility = View.GONE }
                .start()
            scrim.animate().alpha(0f).setDuration(160)
                .withEndAction { scrim.visibility = View.GONE }
                .start()
        }
    }

    // ---- 路线向导 ----

    private fun observeRoute() {
        lifecycleScope.launch {
            RouteSession.plan.collect { plan ->
                drawRoute(plan)
                refreshGuide()
                binding.btnDrawerClearRoute.visibility =
                    if (plan == null) View.GONE else View.VISIBLE
            }
        }
        lifecycleScope.launch {
            RouteSession.visited.collect { refreshGuide() }
        }
        startLocation()
    }

    private fun drawRoute(plan: RoutePlan?) {
        routeMarkers.forEach { it.remove() }
        routeMarkers.clear()
        routeLine?.remove()
        routeLine = null

        if (plan == null) {
            binding.routePanel.visibility = View.GONE
            return
        }

        binding.routePanel.visibility = View.VISIBLE
        binding.txtRouteName.text = plan.routeName

        val located = plan.stops.filter { it.located }
        located.forEach { stop ->
            routeMarkers += aMap.addMarker(
                MarkerOptions()
                    .position(LatLng(stop.lat, stop.lng))
                    .title("${stop.seq}. ${stop.name}")
                    .snippet(stop.reason)
            )
        }
        if (located.size >= 2) {
            routeLine = aMap.addPolyline(
                PolylineOptions()
                    .addAll(located.map { LatLng(it.lat, it.lng) })
                    .width(9f)
                    .color(colorOf(R.attr.shAccent))
            )
        }
        if (located.isNotEmpty()) {
            val builder = LatLngBounds.Builder()
            located.forEach { builder.include(LatLng(it.lat, it.lng)) }
            runCatching {
                aMap.animateCamera(CameraUpdateFactory.newLatLngBounds(builder.build(), 140))
            }
        }
    }

    private fun refreshGuide() {
        val plan = RouteSession.current()
        if (plan == null) {
            binding.routePanel.visibility = View.GONE
            return
        }
        binding.routePanel.visibility = View.VISIBLE
        val index = RouteSession.nextIndex()
        val stop = plan.stops.getOrNull(index)
        if (stop == null) {
            binding.txtRouteNext.text = "这条路线走完了。"
            binding.txtRouteDist.text = "可以在侧栏里结束路线，或者再排一条新的。"
            return
        }
        binding.txtRouteNext.text = buildString {
            append("第 ${stop.seq} 站　${stop.name}")
            if (stop.area.isNotBlank()) append(" · ${stop.area}")
        }

        val me = myLatLng
        if (me == null) {
            binding.txtRouteDist.text = "打开定位后，这里会显示你到这一站的距离。"
            return
        }
        if (!stop.located) {
            binding.txtRouteDist.text = "这一站没能定位到坐标，按备注里的区域找过去。"
            return
        }
        val out = FloatArray(1)
        Location.distanceBetween(me.latitude, me.longitude, stop.lat, stop.lng, out)
        val meters = out[0]
        binding.txtRouteDist.text = when {
            meters >= 1000f -> "离你约 %.1f 公里".format(meters / 1000f)
            else -> "离你约 ${meters.toInt()} 米"
        }
        if (meters < ARRIVE_METERS) {
            RouteSession.markVisited(index)
            Toast.makeText(this, "到了：${stop.name}", Toast.LENGTH_SHORT).show()
            startVibrateHint()
        }
    }

    private fun startVibrateHint() {
        val vibrator = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            (getSystemService(VIBRATOR_MANAGER_SERVICE) as? android.os.VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(VIBRATOR_SERVICE) as? android.os.Vibrator
        }
        runCatching { vibrator?.vibrate(40L) }
    }

    private fun endRoute() {
        RouteSession.clear()
        binding.routePanel.visibility = View.GONE
        binding.btnDrawerClearRoute.visibility = View.GONE
    }

    // ---- 定位 ----

    private fun startLocation() {
        val granted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ),
                REQ_LOCATION
            )
            return
        }
        if (locationClient != null) return

        val client = AMapLocationClient(applicationContext)
        client.setLocationOption(
            AMapLocationClientOption().apply {
                locationMode = AMapLocationClientOption.AMapLocationMode.Hight_Accuracy
                interval = 3000L
                isNeedAddress = false
                isOnceLocation = false
            }
        )
        client.setLocationListener { location ->
            if (location != null && location.errorCode == 0) {
                val here = LatLng(location.latitude, location.longitude)
                myLatLng = here
        LocationHolder.update(here.latitude, here.longitude)
                // 打开应用先站到用户脚下；已经有了路线就交给路线的取景，不抢镜头
                if (!movedToUser) {
                    movedToUser = true
                    if (RouteSession.current() == null) {
                        aMap.animateCamera(
                            CameraUpdateFactory.newLatLngZoom(here, USER_ZOOM)
                        )
                    }
                }
                refreshGuide()
            }
        }
        client.startLocation()
        locationClient = client
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_LOCATION &&
            grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED
        ) {
            startLocation()
        }
    }

    // ---- 生命周期 ----

    override fun onResume() {
        super.onResume()
        binding.mapView.onResume()
        refreshGuide()
        // 从设置页回来可能改过地标筛选
        loadPolicy()
        binding.txtDrawerPalette.text =
            "${ThemeState.palette.label}配色 · ${ThemeState.palette.blurb} · ${if (ThemeState.light) "亮色" else "暗色"}"
    }

    override fun onPause() {
        super.onPause()
        binding.mapView.onPause()
    }

    override fun onDestroy() {
        resolveJob?.cancel()
        locationClient?.stopLocation()
        locationClient?.onDestroy()
        locationClient = null
        binding.mapView.onDestroy()
        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        binding.mapView.onSaveInstanceState(outState)
    }
}
