package com.shanghai.memory.ui.theme

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.shanghai.memory.R

/** 读主题令牌色，替代代码里写死的 R.color.xxx */
@ColorInt
fun Context.colorOf(@AttrRes attr: Int): Int {
    val ta = obtainStyledAttributes(intArrayOf(attr))
    return try {
        ta.getColor(0, ContextCompat.getColor(this, R.color.paper))
    } finally {
        ta.recycle()
    }
}

/** 进程级字体缓存：内置字体 17MB，绝不能每个界面重新读一遍 */
object AppFonts {
    @Volatile
    private var instance: FontCache? = null

    fun of(context: Context): FontCache =
        instance ?: synchronized(this) {
            instance ?: FontCache(context.applicationContext).also { instance = it }
        }
}

/**
 * 所有页面继承它。三件事：
 * 1. 进入前套用当前配色的静态主题（用户改过配色时，回到前台自动重建）；
 * 2. 内容避让系统栏——targetSdk 35 起系统强制 edge-to-edge；
 * 3. 把用户自定义的主色/字体刷到控件上（静态主题只能给「初始色」）。
 */
open class ThemedActivity : AppCompatActivity() {

    /** 默认让根布局整体避开系统栏；地图页要全屏铺图，覆写成 false 自己安排。 */
    protected open val autoInsetPadding: Boolean get() = true

    private var appliedRevision = -1
    private var appliedLanguage = ThemeState.language

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase, ThemeState.language))
    }

    /** 每个控件各自的原始 padding，重复收到 insets 回调时不会越加越大 */
    private val basePaddings = java.util.WeakHashMap<View, IntArray>()

    protected val fonts: FontCache get() = AppFonts.of(this)

    /**
     * 所有页面跳转统一带过渡，调用方不用各自 overridePendingTransition。
     * 想换更花哨的动效时只改 [ThemeFx]，界面代码不动。
     */
    override fun startActivity(intent: android.content.Intent) {
        super.startActivity(intent)
        applyOpenAnimation()
    }

    override fun startActivity(intent: android.content.Intent, options: android.os.Bundle?) {
        super.startActivity(intent, options)
        applyOpenAnimation()
    }

    override fun finish() {
        super.finish()
        applyCloseAnimation()
    }

    @Suppress("DEPRECATION")
    protected open fun applyOpenAnimation() {
        val (enter, exit) = ThemeFx.activityIn()
        overridePendingTransition(enter, exit)
    }

    @Suppress("DEPRECATION")
    protected open fun applyCloseAnimation() {
        val (enter, exit) = ThemeFx.activityOut()
        overridePendingTransition(enter, exit)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        appliedRevision = ThemeState.revision
        appliedLanguage = ThemeState.language
        setTheme(ThemeState.styleRes)
        super.onCreate(savedInstanceState)
        // 亮色底 → 系统栏用深色图标；深色底反之。不做这一步，亮色下状态栏图标会看不见
        val light = ThemeState.light
        val bar = if (light) {
            SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        } else {
            SystemBarStyle.dark(Color.TRANSPARENT)
        }
        enableEdgeToEdge(statusBarStyle = bar, navigationBarStyle = bar)
    }

    override fun setContentView(view: View?) {
        super.setContentView(view)
        if (view != null) afterContent(view)
    }

    override fun setContentView(view: View?, params: ViewGroup.LayoutParams?) {
        super.setContentView(view, params)
        if (view != null) afterContent(view)
    }

    override fun setContentView(layoutResID: Int) {
        super.setContentView(layoutResID)
        findViewById<View>(android.R.id.content)?.let { afterContent(it) }
    }

    private fun afterContent(root: View) {
        if (autoInsetPadding) padForSystemBars(root)
        applyAppearance(root)
    }

    /**
     * 刷一遍用户外观。子类在动态增删控件后可以再调一次。
     */
    protected fun applyAppearance(root: View) {
        runCatching {
            ThemeEngine.recolor(
                root,
                ThemeState.colors,
                fonts.get(ThemeState.font, ThemeState.importedFontPath)
            )
        }
    }

    /**
     * 把系统栏、刘海占用的高度加到控件原有的 padding 上，不覆盖布局里写好的留白。
     * 四个方向可单独开关，地图页用它给顶部条只加顶、给底部卡片只加底。
     *
     * 底部取「导航条」与「软键盘」中较大的那个——开了 edge-to-edge 之后
     * adjustResize 不再自动把界面顶上去，键盘不这样避让就会盖住输入框。
     */
    protected fun padForSystemBars(
        view: View,
        left: Boolean = true,
        top: Boolean = true,
        right: Boolean = true,
        bottom: Boolean = true
    ) {
        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            val base = basePaddings.getOrPut(v) {
                intArrayOf(v.paddingLeft, v.paddingTop, v.paddingRight, v.paddingBottom)
            }
            v.setPadding(
                base[0] + if (left) bars.left else 0,
                base[1] + if (top) bars.top else 0,
                base[2] + if (right) bars.right else 0,
                base[3] + if (bottom) maxOf(bars.bottom, ime) else 0
            )
            insets
        }
        ViewCompat.requestApplyInsets(view)
    }

    /** 只取状态栏高度，用于把浮层往下推（不改 padding 的场合）。 */
    protected fun statusBarHeight(view: View): Int {
        val insets = ViewCompat.getRootWindowInsets(view) ?: return 0
        return insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
    }

    override fun onResume() {
        super.onResume()
        if (appliedRevision != ThemeState.revision || appliedLanguage != ThemeState.language) {
            recreate()
        }
    }
}
