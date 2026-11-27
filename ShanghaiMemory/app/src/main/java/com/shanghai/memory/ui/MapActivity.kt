package com.shanghai.memory.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.location.Location
import android.os.Bundle
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.amap.api.location.AMapLocationClient
import com.amap.api.location.AMapLocationClientOption
import com.amap.api.maps.AMap
import com.amap.api.maps.CameraUpdateFactory
import com.amap.api.maps.model.BitmapDescriptorFactory
import com.amap.api.maps.model.CameraPosition
import com.amap.api.maps.model.Circle
import com.amap.api.maps.model.CircleOptions
import com.amap.api.maps.model.LatLng
import com.amap.api.maps.model.LatLngBounds
import com.amap.api.maps.model.Marker
import com.amap.api.maps.model.MarkerOptions
import com.amap.api.maps.model.Polyline
import com.amap.api.maps.model.PolylineOptions
import com.google.android.material.dialog.MaterialAlertDialogBuilder
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * F1 地图漫游 + 实时位置导航：
 * 游标固定在屏幕中心，地图在下面移动，停下即辨认脚下地点；
 * 开启侧栏「实时位置导航」后，实时显示观众位置与朝向，屏幕随面朝方向平滑旋转。
 */
class MapActivity : ThemedActivity() {

    private companion object {
        const val DEFAULT_LAT = 31.2397
        const val DEFAULT_LNG = 121.4900
        const val DEFAULT_ZOOM = 16f
        const val USER_ZOOM = 17f
        const val NAV_ZOOM = 17.5f
        const val NAV_TILT = 24f
        const val SEARCH_ZOOM = 17f
        const val DEBOUNCE_MS = 400L
        const val ARRIVE_METERS = 80f
        const val NAV_RESOLVE_METERS = 35f
        const val REQ_LOCATION = 1001
        const val DRAWER_WIDTH_DP = 298
        const val KEY_LIVE_NAV = "live_nav_enabled"
        const val KEY_NAV_FOLLOW = "nav_following"
        const val KEY_MOVED_TO_USER = "moved_to_user"

        const val NEAR_METERS = 120f
        val SEARCH_RADII = intArrayOf(120, 400, 900)

        val NOISE_WORDS = listOf(
            "便利店", "超市", "加油站", "停车场", "公共厕所", "卫生间",
            "ATM", "充电站", "快递", "菜鸟驿站"
        )
    }

    private lateinit var binding: ActivityMapBinding
    private lateinit var aMap: AMap
    private lateinit var amapService: AmapService
    private lateinit var orientationTracker: OrientationTracker

    private var resolveJob: Job? = null
    private var currentPoi: PoiInfo? = null
    private var lastResolvedKey: String? = null
    private var candidates: List<PoiInfo> = emptyList()
    private var policy: GatePolicy = GatePolicy()
    private val store: SettingsStore by lazy { SettingsStore(this) }
    private var requestSeq = 0

    private var locationClient: AMapLocationClient? = null
    private var myLatLng: LatLng? = null
    private var lastAccuracy: Float = 20f

    private val routeMarkers = mutableListOf<Marker>()
    private var routeLine: Polyline? = null

    private var drawerOpen = false
    private val drawerBackCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            toggleDrawer(false)
        }
    }
    private var movedToUser = false

    // 实时位置与朝向导航状态
    private var liveNavEnabled = false
    private var navFollowing = false
    private var pendingEnableLiveNav = false
    private var currentAzimuth = 0f
    private var isProgrammaticCamera = false
    private var navAnimUntilMs = 0L
    private var lastNavResolveLatLng: LatLng? = null
    private var navMarker: Marker? = null
    private var navAccuracyCircle: Circle? = null
    private var touchDownX = 0f
    private var touchDownY = 0f

    override val autoInsetPadding: Boolean get() = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMapBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.mapView.onCreate(savedInstanceState)
        aMap = binding.mapView.map
        amapService = AmapService(this)
        orientationTracker = OrientationTracker(this) { azimuth ->
            onHeadingChanged(azimuth)
        }

        setUpInsets()
        setUpMap()
        setUpActions()
        observeRoute()
        setUpDrawer()
        loadPolicy()
        maybeShowOnboarding()

        if (LocationHolder.hasFix) {
            myLatLng = LatLng(LocationHolder.lat, LocationHolder.lng)
        }
        if (savedInstanceState != null) {
            movedToUser = savedInstanceState.getBoolean(KEY_MOVED_TO_USER, false)
            val savedNav = savedInstanceState.getBoolean(KEY_LIVE_NAV, false)
            val savedFollow = savedInstanceState.getBoolean(KEY_NAV_FOLLOW, true)
            if (savedNav) {
                restoreLiveNavState(savedFollow)
            }
        }
    }

    private fun maybeShowOnboarding() {
        lifecycleScope.launch {
            if (store.current().onboarded) return@launch
            startActivity(OnboardingActivity.intent(this@MapActivity))
        }
    }

    private fun loadPolicy() {
        lifecycleScope.launch {
            val settings = store.current()
            binding.actionBar.visibility = if (settings.showQuickBar) View.VISIBLE else View.GONE
            val fresh = GatePolicy.of(settings)
            if (fresh == policy) return@launch
            policy = fresh
            lastResolvedKey = null
            val target = aMap.cameraPosition?.target ?: LatLng(DEFAULT_LAT, DEFAULT_LNG)
            resolveAt(target.latitude, target.longitude)
        }
    }

    // ---- 系统栏避让 ----

    private fun setUpInsets() {
        padForSystemBars(binding.topOverlay, bottom = false)
        padForSystemBars(binding.rightControls, left = false, right = false, bottom = false)
        liftAboveNavigationBar(binding.bottomDock)
        padForSystemBars(binding.drawerPanel)
    }

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
            isRotateGesturesEnabled = true
            isTiltGesturesEnabled = true
        }
        aMap.moveCamera(
            CameraUpdateFactory.newLatLngZoom(LatLng(DEFAULT_LAT, DEFAULT_LNG), DEFAULT_ZOOM)
        )
        aMap.setOnMapTouchListener { event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    touchDownX = event.x
                    touchDownY = event.y
                }

                MotionEvent.ACTION_MOVE -> {
                    val moved = abs(event.x - touchDownX) + abs(event.y - touchDownY)
                    if (moved > 18f && liveNavEnabled && navFollowing) {
                        pauseNavFollow()
                    }
                }
            }
        }
        aMap.setOnCameraChangeListener(object : AMap.OnCameraChangeListener {
            override fun onCameraChange(position: CameraPosition) = Unit

            override fun onCameraChangeFinish(position: CameraPosition) {
                if (isProgrammaticCamera) {
                    isProgrammaticCamera = false
                    return
                }
                // 实时跟随时镜头由指南针/定位驱动，不从相机回调反复触发辨认，避免在无地标区域死循环取消防抖
                if (liveNavEnabled && navFollowing) return
                resolveAt(position.target.latitude, position.target.longitude)
            }
        })
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
        binding.btnNav.setOnClickListener { toggleLiveNavigation() }
        binding.btnChat.setOnClickListener {
            startActivity(ChatActivity.open(this))
        }
        binding.btnRoute.setOnClickListener {
            startActivity(Intent(this, RouteActivity::class.java))
        }
        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        binding.btnRecenter.setOnClickListener {
            resumeNavFollow()
        }
        binding.navStatusPill.setOnClickListener {
            if (liveNavEnabled && !navFollowing) {
                resumeNavFollow()
            }
        }
        binding.btnRouteExit.setOnClickListener { endRoute() }
    }

    // ---- 实时位置与朝向导航 ----

    private fun toggleLiveNavigation() {
        if (liveNavEnabled) {
            stopLiveNavigation()
        } else {
            startLiveNavigation()
        }
    }

    private fun startLiveNavigation() {
        val granted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            pendingEnableLiveNav = true
            Toast.makeText(this, R.string.nav_need_location, Toast.LENGTH_SHORT).show()
            startLocation()
            return
        }
        pendingEnableLiveNav = false
        liveNavEnabled = true
        navFollowing = true
        lastNavResolveLatLng = null

        restartLocationClient(intervalMs = 1500L)
        if (orientationTracker.hasCompass) {
            currentAzimuth = orientationTracker.currentAzimuth
            orientationTracker.start()
        }
        binding.navStatusPill.visibility = View.VISIBLE
        binding.btnRecenter.visibility = View.GONE
        updateNavPillText()
        updateNavDrawerUi()
        updateNavPuck()

        val target = myLatLng ?: aMap.cameraPosition?.target ?: LatLng(DEFAULT_LAT, DEFAULT_LNG)
        val zoom = maxOf(aMap.cameraPosition?.zoom ?: DEFAULT_ZOOM, NAV_ZOOM)
        isProgrammaticCamera = true
        navAnimUntilMs = SystemClock.elapsedRealtime() + 450L
        aMap.animateCamera(
            CameraUpdateFactory.newCameraPosition(
                CameraPosition(target, zoom, NAV_TILT, currentAzimuth)
            ),
            400L,
            object : AMap.CancelableCallback {
                override fun onFinish() {
                    isProgrammaticCamera = false
                    navAnimUntilMs = 0L
                }

                override fun onCancel() {
                    isProgrammaticCamera = false
                    navAnimUntilMs = 0L
                }
            }
        )
        if (myLatLng != null) {
            lastNavResolveLatLng = myLatLng
            resolveAt(target.latitude, target.longitude)
        }
        Toast.makeText(this, R.string.nav_started_toast, Toast.LENGTH_SHORT).show()
    }

    private fun restoreLiveNavState(follow: Boolean) {
        val granted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) return
        liveNavEnabled = true
        navFollowing = follow
        restartLocationClient(intervalMs = 1500L)
        if (orientationTracker.hasCompass) {
            currentAzimuth = orientationTracker.currentAzimuth
            orientationTracker.start()
        }
        binding.navStatusPill.visibility = View.VISIBLE
        binding.btnRecenter.visibility = if (navFollowing) View.GONE else View.VISIBLE
        updateNavPillText()
        updateNavDrawerUi()
        if (follow && myLatLng != null) {
            binding.mapView.post { resumeNavFollow() }
        }
    }

    private fun stopLiveNavigation() {
        liveNavEnabled = false
        navFollowing = false
        pendingEnableLiveNav = false
        orientationTracker.stop()
        clearNavPuck()
        binding.navStatusPill.visibility = View.GONE
        binding.btnRecenter.visibility = View.GONE
        updateNavDrawerUi()
        restartLocationClient(intervalMs = 3000L)

        val cur = aMap.cameraPosition
        if (cur != null) {
            isProgrammaticCamera = true
            aMap.animateCamera(
                CameraUpdateFactory.newCameraPosition(
                    CameraPosition(cur.target, cur.zoom, 0f, 0f)
                ),
                320L,
                object : AMap.CancelableCallback {
                    override fun onFinish() {
                        isProgrammaticCamera = false
                    }

                    override fun onCancel() {
                        isProgrammaticCamera = false
                    }
                }
            )
        }
        Toast.makeText(this, R.string.nav_stopped_toast, Toast.LENGTH_SHORT).show()
    }

    private fun pauseNavFollow() {
        if (!navFollowing) return
        navFollowing = false
        binding.btnRecenter.visibility = View.VISIBLE
        updateNavPillText()
    }

    private fun resumeNavFollow() {
        if (!liveNavEnabled) return
        navFollowing = true
        binding.btnRecenter.visibility = View.GONE
        updateNavPillText()
        val target = myLatLng ?: return
        val zoom = maxOf(aMap.cameraPosition?.zoom ?: DEFAULT_ZOOM, NAV_ZOOM)
        isProgrammaticCamera = true
        navAnimUntilMs = SystemClock.elapsedRealtime() + 450L
        aMap.animateCamera(
            CameraUpdateFactory.newCameraPosition(
                CameraPosition(target, zoom, NAV_TILT, currentAzimuth)
            ),
            350L,
            object : AMap.CancelableCallback {
                override fun onFinish() {
                    isProgrammaticCamera = false
                    navAnimUntilMs = 0L
                }

                override fun onCancel() {
                    isProgrammaticCamera = false
                    navAnimUntilMs = 0L
                }
            }
        )
    }

    private fun onHeadingChanged(azimuth: Float) {
        currentAzimuth = azimuth
        if (!liveNavEnabled) return
        navMarker?.rotateAngle = (360f - azimuth) % 360f
        updateNavPillText()
        if (navFollowing && SystemClock.elapsedRealtime() >= navAnimUntilMs) {
            val target = myLatLng ?: aMap.cameraPosition?.target ?: return
            val cur = aMap.cameraPosition
            val zoom = maxOf(cur?.zoom ?: NAV_ZOOM, 15.5f)
            val tilt = cur?.tilt?.takeIf { it >= 5f } ?: NAV_TILT
            isProgrammaticCamera = true
            aMap.moveCamera(
                CameraUpdateFactory.newCameraPosition(
                    CameraPosition(target, zoom, tilt, azimuth)
                )
            )
            // moveCamera 不会每次都触发 onCameraChangeFinish，主动复位避免吞掉下一次手势
            binding.mapView.post { isProgrammaticCamera = false }
        }
    }

    private fun updateNavPillText() {
        binding.txtNavPill.text = when {
            !navFollowing -> getString(R.string.nav_pill_paused)
            !orientationTracker.hasCompass -> getString(R.string.nav_pill_no_compass)
            else -> getString(
                R.string.nav_pill_active,
                cardinalDirection(currentAzimuth),
                currentAzimuth.toInt()
            )
        }
    }

    private fun cardinalDirection(deg: Float): String {
        val normalized = ((deg % 360f) + 360f) % 360f
        val idx = (((normalized + 22.5f) / 45f).toInt()) % 8
        val resId = when (idx) {
            0 -> R.string.dir_n
            1 -> R.string.dir_ne
            2 -> R.string.dir_e
            3 -> R.string.dir_se
            4 -> R.string.dir_s
            5 -> R.string.dir_sw
            6 -> R.string.dir_w
            else -> R.string.dir_nw
        }
        return getString(resId)
    }

    private fun updateNavPuck() {
        val pos = myLatLng ?: return
        val colors = ThemeState.colors
        val fillColor = (colors.accent and 0x00FFFFFF) or (0x24 shl 24)
        val strokeColor = (colors.accent and 0x00FFFFFF) or (0x66 shl 24)

        val circle = navAccuracyCircle
        if (circle == null) {
            navAccuracyCircle = aMap.addCircle(
                CircleOptions()
                    .center(pos)
                    .radius(lastAccuracy.coerceIn(10f, 120f).toDouble())
                    .fillColor(fillColor)
                    .strokeColor(strokeColor)
                    .strokeWidth(2f)
                    .zIndex(8f)
            )
        } else {
            circle.center = pos
            circle.radius = lastAccuracy.coerceIn(10f, 120f).toDouble()
            circle.fillColor = fillColor
            circle.strokeColor = strokeColor
        }

        val marker = navMarker
        if (marker == null) {
            navMarker = aMap.addMarker(
                MarkerOptions()
                    .position(pos)
                    .anchor(0.5f, 0.5f)
                    .setFlat(true)
                    .zIndex(10f)
                    .icon(BitmapDescriptorFactory.fromBitmap(createNavPuckBitmap()))
            )
        } else {
            marker.position = pos
        }
        navMarker?.rotateAngle = (360f - currentAzimuth) % 360f
    }

    private fun clearNavPuck() {
        navMarker?.remove()
        navMarker = null
        navAccuracyCircle?.remove()
        navAccuracyCircle = null
    }

    private fun createNavPuckBitmap(): Bitmap {
        val density = resources.displayMetrics.density
        val size = (72 * density).toInt()
        val cx = size / 2f
        val cy = size / 2f
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val colors = ThemeState.colors

        // 朝上（-90°）的方向扇形光束
        val coneRadius = 32 * density
        val conePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = (colors.accent and 0x00FFFFFF) or (0x4D shl 24)
            style = Paint.Style.FILL
        }
        val oval = RectF(cx - coneRadius, cy - coneRadius, cx + coneRadius, cy + coneRadius)
        canvas.drawArc(oval, -125f, 70f, true, conePaint)

        // 外围白/亮环阴影
        val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFFFFF.toInt()
            style = Paint.Style.FILL
            setShadowLayer(4 * density, 0f, 1 * density, 0x55000000)
        }
        canvas.drawCircle(cx, cy, 11.5f * density, ringPaint)

        // 主题色核心圆点
        val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colors.accent
            style = Paint.Style.FILL
        }
        canvas.drawCircle(cx, cy, 8.2f * density, dotPaint)

        // 顶部小箭头，指示正前方
        val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colors.accentBright
            style = Paint.Style.FILL
        }
        val arrow = Path().apply {
            moveTo(cx, cy - 17.5f * density)
            lineTo(cx - 5f * density, cy - 10.5f * density)
            lineTo(cx + 5f * density, cy - 10.5f * density)
            close()
        }
        canvas.drawPath(arrow, arrowPaint)
        return bmp
    }

    // ---- F1 辨认脚下地点 ----

    private fun resolveAt(lat: Double, lng: Double) {
        val key = "%.4f,%.4f".format(lat, lng)
        if (key == lastResolvedKey) return

        val seq = ++requestSeq
        resolveJob?.cancel()
        resolveJob = lifecycleScope.launch {
            binding.txtStatus.setText(R.string.map_searching)
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

            if (seq != requestSeq) return@launch

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

    private suspend fun collectCandidates(lat: Double, lng: Double): CandidateScan {
        val pool = mutableListOf<PoiInfo>()
        var nearestRejected: PoiInfo? = null
        for (radius in SEARCH_RADII) {
            val hits = amapService.nearby(lat, lng, radius)
            for (poi in hits) {
                val ok = LandmarkGate.isWorthTelling(poi, policy) &&
                    NOISE_WORDS.none { poi.name.contains(it) }
                if (ok) {
                    pool += poi
                } else if (nearestRejected == null) {
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

    private data class CandidateScan(val passed: List<PoiInfo>, val rejected: PoiInfo?)

    // ---- 地点搜索 ----

    private fun showSearchDialog() {
        val pad = (20 * resources.displayMetrics.density).toInt()
        val input = EditText(this).apply {
            hint = getString(R.string.search_hint)
            setSingleLine()
            setTextColor(colorOf(R.attr.shText))
            setHintTextColor(colorOf(R.attr.shTextMuted))
        }
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

    private fun focusOn(poi: PoiInfo) {
        if (liveNavEnabled && navFollowing) pauseNavFollow()
        val adjusted = if (poi.distanceMeters <= 0) {
            val here = myLatLng
            if (here != null) {
                val res = FloatArray(1)
                Location.distanceBetween(here.latitude, here.longitude, poi.lat, poi.lng, res)
                poi.copy(distanceMeters = res[0].toInt())
            } else {
                poi.copy(distanceMeters = -1)
            }
        } else {
            poi
        }
        candidates = listOf(adjusted)
        applyPoi(adjusted)
        lastResolvedKey = "%.4f,%.4f".format(adjusted.lat, adjusted.lng)
        isProgrammaticCamera = true
        aMap.animateCamera(
            CameraUpdateFactory.newLatLngZoom(LatLng(adjusted.lat, adjusted.lng), SEARCH_ZOOM),
            400L,
            object : AMap.CancelableCallback {
                override fun onFinish() {
                    isProgrammaticCamera = false
                }

                override fun onCancel() {
                    isProgrammaticCamera = false
                }
            }
        )
    }

    private fun applyPoi(poi: PoiInfo) {
        currentPoi = poi
        binding.txtPoiName.text = poi.name
        binding.txtPoiMeta.text = buildMeta(poi)
        binding.txtStatus.text = when {
            poi.distanceMeters < 0 -> poi.name
            poi.distanceMeters <= NEAR_METERS -> getString(R.string.map_here_found, poi.name)
            else -> {
                val distText = if (ThemeState.isEnglish) {
                    if (poi.distanceMeters < 950) "~${poi.distanceMeters} m" else "~%.1f km".format(poi.distanceMeters / 1000f)
                } else {
                    if (poi.distanceMeters < 950) "约 ${poi.distanceMeters} 米" else "约 %.1f 公里".format(poi.distanceMeters / 1000f)
                }
                getString(R.string.map_nearby_found, poi.name, distText)
            }
        }
        renderCandidates()
    }

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
            val type = LandmarkGate.friendlyType(poi.type).ifBlank { getString(R.string.map_default_type) }
            if (ThemeState.isEnglish) {
                "${poi.name}\n$type · ~${poi.distanceMeters} m"
            } else {
                "${poi.name}\n$type · 约 ${poi.distanceMeters} 米"
            }
        }.toTypedArray()
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.map_alt_title)
            .setItems(items) { _, which ->
                candidates.getOrNull(which)?.let { applyPoi(it) }
            }
            .setNegativeButton(R.string.common_cancel, null)
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
        onBackPressedDispatcher.addCallback(this, drawerBackCallback)
        binding.btnMenu.setOnClickListener { toggleDrawer(!drawerOpen) }
        binding.drawerScrim.setOnClickListener { toggleDrawer(false) }
        binding.btnDrawerNav.setOnClickListener {
            toggleDrawer(false)
            toggleLiveNavigation()
        }
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
        updateNavDrawerUi()
        updateDrawerPaletteLabel()
    }

    private fun updateNavDrawerUi() {
        val colors = ThemeState.colors
        binding.txtDrawerNavState.text = getString(
            if (liveNavEnabled) R.string.drawer_nav_on else R.string.drawer_nav_off
        )
        binding.txtDrawerNavState.setTextColor(
            if (liveNavEnabled) colors.accent else colors.textMuted
        )
        binding.icDrawerNav.imageTintList = ColorStateList.valueOf(
            if (liveNavEnabled) colors.accentBright else colors.accent
        )
        val bg = binding.btnNav.background?.mutate()
        if (bg is android.graphics.drawable.GradientDrawable) {
            val fill = if (liveNavEnabled) {
                colors.accent
            } else {
                com.shanghai.memory.ui.theme.ThemeEngine.withSurfaceAlpha(
                    colors.card,
                    com.shanghai.memory.ui.theme.Role.CARD
                )
            }
            val strokePx = kotlin.math.max(1, (resources.displayMetrics.density).toInt())
            bg.setColor(fill)
            bg.setStroke(strokePx, if (liveNavEnabled) colors.accent else colors.line)
        }
        binding.btnNav.imageTintList = ColorStateList.valueOf(
            if (liveNavEnabled) colors.onAccent else colors.accent
        )
    }

    private fun updateDrawerPaletteLabel() {
        val lightLabel = getString(
            if (ThemeState.light) R.string.drawer_theme_light else R.string.drawer_theme_dark
        )
        binding.txtDrawerPalette.text = getString(
            R.string.drawer_palette_status,
            ThemeState.palette.label,
            lightLabel
        )
    }

    private fun toggleDrawer(open: Boolean) {
        if (drawerOpen == open) return
        drawerOpen = open
        drawerBackCallback.isEnabled = open
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
        if (located.isNotEmpty() && !liveNavEnabled) {
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
        binding.progressRoute.progressTintList = ColorStateList.valueOf(ThemeState.colors.accent)
        val total = plan.stops.size.coerceAtLeast(1)
        val index = RouteSession.nextIndex()
        val stop = plan.stops.getOrNull(index)
        if (stop == null) {
            binding.txtRouteProgress.text = getString(R.string.map_route_stop_progress, total, total)
            binding.progressRoute.progress = 100
            binding.txtRouteNext.setText(R.string.map_route_done_title)
            binding.txtRouteDist.setText(R.string.map_route_done_desc)
            return
        }
        val currentStep = (index + 1).coerceIn(1, total)
        binding.txtRouteProgress.text = getString(R.string.map_route_stop_progress, currentStep, total)
        binding.progressRoute.progress = ((index.toFloat() / total.toFloat()) * 100f).toInt()
        binding.txtRouteNext.text = buildString {
            append(getString(R.string.map_route_stop_title, stop.seq, stop.name))
            if (stop.area.isNotBlank()) append(" · ${stop.area}")
        }

        val me = myLatLng
        if (me == null) {
            binding.txtRouteDist.setText(R.string.map_route_need_gps)
            return
        }
        if (!stop.located) {
            binding.txtRouteDist.setText(R.string.map_route_no_coord)
            return
        }
        val out = FloatArray(1)
        Location.distanceBetween(me.latitude, me.longitude, stop.lat, stop.lng, out)
        val meters = out[0]
        binding.txtRouteDist.text = when {
            meters >= 1000f -> getString(R.string.map_route_dist_km, meters / 1000f)
            else -> getString(R.string.map_route_dist_m, meters.toInt())
        }
        if (meters < ARRIVE_METERS) {
            RouteSession.markVisited(index)
            Toast.makeText(this, getString(R.string.map_route_arrived, stop.name), Toast.LENGTH_SHORT).show()
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

    private fun startLocation(intervalMs: Long = 3000L) {
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
                interval = intervalMs
                isNeedAddress = false
                isOnceLocation = false
            }
        )
        client.setLocationListener { location ->
            if (location != null && location.errorCode == 0) {
                val here = LatLng(location.latitude, location.longitude)
                myLatLng = here
                lastAccuracy = location.accuracy
                LocationHolder.update(here.latitude, here.longitude)

                // 如果设备没有磁力计/旋转传感器，用 GPS 行进方向作为降级朝向
                if (liveNavEnabled && !orientationTracker.hasCompass && location.hasBearing() && location.speed > 0.4f) {
                    currentAzimuth = location.bearing
                }

                if (!movedToUser) {
                    movedToUser = true
                    if (RouteSession.current() == null && !liveNavEnabled) {
                        isProgrammaticCamera = true
                        aMap.animateCamera(
                            CameraUpdateFactory.newLatLngZoom(here, USER_ZOOM),
                            400L,
                            object : AMap.CancelableCallback {
                                override fun onFinish() {
                                    isProgrammaticCamera = false
                                }

                                override fun onCancel() {
                                    isProgrammaticCamera = false
                                }
                            }
                        )
                    }
                }

                if (liveNavEnabled) {
                    updateNavPuck()
                    if (navFollowing) {
                        if (SystemClock.elapsedRealtime() >= navAnimUntilMs) {
                            val cur = aMap.cameraPosition
                            val zoom = maxOf(cur?.zoom ?: NAV_ZOOM, 15.5f)
                            val tilt = cur?.tilt?.takeIf { it >= 5f } ?: NAV_TILT
                            isProgrammaticCamera = true
                            aMap.moveCamera(
                                CameraUpdateFactory.newCameraPosition(
                                    CameraPosition(here, zoom, tilt, currentAzimuth)
                                )
                            )
                            binding.mapView.post { isProgrammaticCamera = false }
                        }

                        val last = lastNavResolveLatLng
                        val movedEnough = if (last == null) {
                            true
                        } else {
                            val d = FloatArray(1)
                            Location.distanceBetween(last.latitude, last.longitude, here.latitude, here.longitude, d)
                            d[0] >= NAV_RESOLVE_METERS
                        }
                        if (movedEnough) {
                            lastNavResolveLatLng = here
                            resolveAt(here.latitude, here.longitude)
                        }
                    }
                }
                refreshGuide()
            }
        }
        client.startLocation()
        locationClient = client
    }

    private fun restartLocationClient(intervalMs: Long) {
        runCatching {
            locationClient?.stopLocation()
            locationClient?.onDestroy()
        }
        locationClient = null
        startLocation(intervalMs)
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
            if (pendingEnableLiveNav) {
                startLiveNavigation()
            }
        } else if (requestCode == REQ_LOCATION) {
            pendingEnableLiveNav = false
        }
    }

    // ---- 生命周期 ----

    override fun onResume() {
        super.onResume()
        binding.mapView.onResume()
        refreshGuide()
        loadPolicy()
        updateDrawerPaletteLabel()
        updateNavDrawerUi()
        if (liveNavEnabled) {
            if (orientationTracker.hasCompass) orientationTracker.start()
            navMarker?.setIcon(BitmapDescriptorFactory.fromBitmap(createNavPuckBitmap()))
            updateNavPuck()
            updateNavPillText()
        }
    }

    override fun onPause() {
        super.onPause()
        orientationTracker.stop()
        binding.mapView.onPause()
    }

    override fun onDestroy() {
        resolveJob?.cancel()
        orientationTracker.stop()
        clearNavPuck()
        locationClient?.stopLocation()
        locationClient?.onDestroy()
        locationClient = null
        binding.mapView.onDestroy()
        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(KEY_LIVE_NAV, liveNavEnabled)
        outState.putBoolean(KEY_NAV_FOLLOW, navFollowing)
        outState.putBoolean(KEY_MOVED_TO_USER, movedToUser)
        super.onSaveInstanceState(outState)
        binding.mapView.onSaveInstanceState(outState)
    }
}
