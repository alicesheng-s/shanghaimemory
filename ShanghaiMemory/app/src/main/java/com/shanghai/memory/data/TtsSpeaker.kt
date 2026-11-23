package com.shanghai.memory.data

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/**
 * 系统 TTS 朗读。
 *
 * 这里比想象中脆：国产 ROM 的默认引擎常是厂商自研（小米的 mibrain.speech、华为的 hwtts），
 * 第三方应用调用时要么没装中文语音数据、要么根本不响应，表现就是「点了没声音、也不报错」。
 * 所以这个类不做静默失败：初始化状态、语言可用性、每次 speak 的返回码都记下来，
 * 界面能直接读到，出问题时用户至少知道卡在哪一步。
 */
class TtsSpeaker(context: Context) {

    enum class State { INIT, READY, NO_ENGINE, LANG_MISSING, FAILED }

    private val appContext = context.applicationContext
    private var engine: TextToSpeech? = null
    private var pending: String? = null
    private var pendingSpeed = 1.0f
    private var speakingFlag = false

    /** 当前可用性；界面据此显示提示 */
    @Volatile
    var state: State = State.INIT
        private set

    /** 人话说明，含引擎名与错误码 */
    @Volatile
    var detail: String = "正在初始化语音引擎…"
        private set

    var onStateChanged: ((Boolean) -> Unit)? = null

    /** 每次状态变化都回调，设置页用来刷新提示 */
    var onAvailabilityChanged: ((State, String) -> Unit)? = null

    val isSpeaking: Boolean get() = speakingFlag

    init {
        start(null)
    }

    /** [forceEngine] 指定引擎包名；null 表示先用系统默认 */
    private fun start(forceEngine: String?) {
        val listener = TextToSpeech.OnInitListener { status ->
            if (status == TextToSpeech.SUCCESS) {
                engine ?: return@OnInitListener
                applyLanguage()
            } else {
                // 默认引擎起不来，退到 Google 的引擎再试一次；国内 ROM 上它常常没装
                if (forceEngine == null && hasGoogleEngine(appContext)) {
                    publish(State.INIT, "系统默认引擎不可用，正在改用 Google 语音服务…")
                    engine?.shutdown()
                    engine = null
                    start(GOOGLE_TTS)
                } else {
                    publish(State.NO_ENGINE, "语音引擎启动失败（错误码 $status）。到系统设置的「文字转语音」里换一个引擎再试。")
                }
            }
        }
        engine = if (forceEngine == null) {
            TextToSpeech(appContext, listener)
        } else {
            TextToSpeech(appContext, listener, forceEngine)
        }
    }

    private fun applyLanguage() {
        val e = engine ?: return
        val available = e.isLanguageAvailable(Locale.SIMPLIFIED_CHINESE)
        if (available == TextToSpeech.LANG_MISSING_DATA || available == TextToSpeech.LANG_NOT_SUPPORTED) {
            publish(
                State.LANG_MISSING,
                "当前引擎（${engineName()}）没有中文语音数据。到系统设置的「文字转语音」里下载中文语音包。"
            )
            return
        }
        val result = e.setLanguage(Locale.SIMPLIFIED_CHINESE)
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            publish(State.LANG_MISSING, "引擎拒绝切换到中文（${engineName()}）。")
            return
        }
        e.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                speakingFlag = true
                onStateChanged?.invoke(true)
            }

            override fun onDone(utteranceId: String?) {
                speakingFlag = false
                onStateChanged?.invoke(false)
            }

            @Deprecated("兼容旧 API")
            override fun onError(utteranceId: String?) {
                speakingFlag = false
                onStateChanged?.invoke(false)
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                speakingFlag = false
                onStateChanged?.invoke(false)
            }
        })
        publish(State.READY, "朗读可用：${engineName()}")
        pending?.let { text ->
            pending = null
            speak(text, pendingSpeed)
        }
    }

    /** 引擎自己的名义名称（如「小米语音引擎」），拿不到就退回包名 */
    private fun engineName(): String {
        val e = engine ?: return "未知引擎"
        val default = runCatching { e.defaultEngine }.getOrNull().orEmpty()
        // 引擎自己报的名字最准（小米、华为的引擎在 getApplicationLabel 上常被包可见性挡住）
        val listed = runCatching {
            e.engines.firstOrNull { it.name == default } ?: e.engines.firstOrNull()
        }.getOrNull()
        if (listed != null && listed.label.isNotBlank()) return listed.label
        if (default.isBlank()) return "系统默认引擎"
        val label = runCatching {
            val pm = appContext.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(default, 0)).toString()
        }.getOrNull()
        return label ?: default
    }

    fun speak(text: String, speed: Float) {
        val body = text.trim()
        if (body.isEmpty()) return
        val e = engine
        if (e == null) {
            publish(State.NO_ENGINE, "语音引擎没起来，无法朗读。")
            return
        }
        if (state == State.LANG_MISSING) return
        if (state != State.READY) {
            pending = body
            pendingSpeed = speed
            return
        }
        e.setSpeechRate(speed.coerceIn(0.4f, 2.2f))
        // 第四个参数不能传 null Bundle：部分厂商实现在这里会空指针，表现为「调用成功但没声音」
        val code = e.speak(body, TextToSpeech.QUEUE_FLUSH, Bundle(), UTTERANCE_ID)
        if (code == TextToSpeech.ERROR) {
            publish(State.FAILED, "引擎拒绝朗读（${engineName()}）。可能缺少中文语音数据。")
        }
    }

    fun stop() {
        engine?.stop()
        speakingFlag = false
        onStateChanged?.invoke(false)
    }

    fun shutdown() {
        onStateChanged = null
        onAvailabilityChanged = null
        runCatching { engine?.stop() }
        runCatching { engine?.shutdown() }
        engine = null
        state = State.INIT
    }

    private fun publish(next: State, text: String) {
        state = next
        detail = text
        onAvailabilityChanged?.invoke(next, text)
    }

    companion object {
        private const val UTTERANCE_ID = "shanghai-memory"
        private const val GOOGLE_TTS = "com.google.android.tts"

        /** 系统里装了哪些 TTS 引擎，设置页展示用 */
        fun installedEngines(context: Context): List<String> {
            val intent = Intent(TextToSpeech.Engine.INTENT_ACTION_TTS_SERVICE)
            return context.packageManager
                .queryIntentServices(intent, 0)
                .mapNotNull { it.serviceInfo?.packageName }
                .distinct()
        }

        private fun hasGoogleEngine(context: Context): Boolean =
            installedEngines(context).contains(GOOGLE_TTS)
    }
}
