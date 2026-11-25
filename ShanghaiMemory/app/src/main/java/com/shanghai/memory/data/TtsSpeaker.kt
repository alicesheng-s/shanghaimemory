package com.shanghai.memory.data

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
 *
 * 两个容易踩的坑都在这层处理掉：
 * 一是朗读进度回调走 Binder 线程，界面在里面改控件会抛 CalledFromWrongThreadException，
 * 所以对外回调统一切回主线程；二是构造器在个别实现上会同步回调，那时 engine 还没赋值，
 * 因此 init 结果一律投递到主线程队列尾部再处理。
 */
class TtsSpeaker(context: Context) {

    enum class State { INIT, READY, NO_ENGINE, LANG_MISSING, FAILED }

    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private var engine: TextToSpeech? = null
    private var pending: String? = null
    private var pendingSpeed = 1.0f
    private var speakingFlag = false

    /** 每次 start 递增。切换引擎后旧引擎晚到的 init 回调据此丢弃 */
    private var generation = 0

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
        val myGeneration = ++generation
        val listener = TextToSpeech.OnInitListener { status ->
            // 个别实现会在构造过程中同步回调，此时 engine 还没赋值；
            // 投递到主线程队列尾再处理，实例一定已经就位
            onMain { handleInit(myGeneration, status, forceEngine) }
        }
        val created = if (forceEngine == null) {
            TextToSpeech(appContext, listener)
        } else {
            TextToSpeech(appContext, listener, forceEngine)
        }
        engine = created
    }

    private fun handleInit(myGeneration: Int, status: Int, forceEngine: String?) {
        if (myGeneration != generation) return
        if (status == TextToSpeech.SUCCESS) {
            applyLanguage()
            return
        }
        // 默认引擎起不来，退到 Google 的引擎再试一次；国内 ROM 上它常常没装
        if (forceEngine == null && hasGoogleEngine(appContext)) {
            publish(State.INIT, "系统默认引擎不可用，正在改用 Google 语音服务…")
            runCatching { engine?.shutdown() }
            engine = null
            start(GOOGLE_TTS)
        } else {
            publish(State.NO_ENGINE, "语音引擎启动失败（错误码 $status）。到系统设置的「文字转语音」里换一个引擎再试。")
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
                notifySpeaking(true)
            }

            override fun onDone(utteranceId: String?) {
                notifySpeaking(false)
            }

            @Deprecated("兼容旧 API")
            override fun onError(utteranceId: String?) {
                notifySpeaking(false)
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                notifySpeaking(false)
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
        // 只有还在初始化时才排队。FAILED / NO_ENGINE 若也塞进 pending，
        // 就再也没有第二次 applyLanguage 来兑现它，表现为「永远念不出来、也不报错」
        if (state == State.INIT) {
            pending = body
            pendingSpeed = speed
            return
        }
        e.setSpeechRate(speed.coerceIn(0.4f, 2.2f))
        // 单次朗读有字数上限（通常 4000），长文按句子切成多段塞进队列，避免尾部被吞
        val chunks = splitForTts(body, TextToSpeech.getMaxSpeechInputLength())
        var rejected = false
        chunks.forEachIndexed { index, chunk ->
            val mode = if (index == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
            // 第四个参数不能传 null Bundle：部分厂商实现在这里会空指针，表现为「调用成功但没声音」
            val code = e.speak(chunk, mode, Bundle(), "$UTTERANCE_ID-$index")
            if (code == TextToSpeech.ERROR) rejected = true
        }
        if (rejected) {
            publish(State.FAILED, "引擎拒绝朗读（${engineName()}）。可能缺少中文语音数据。")
        }
    }

    /**
     * 按句号一类断句切成不超过 [limit] 的若干段。
     * 单句本身就超长时硬切，保证每段都不会超过引擎上限。
     */
    private fun splitForTts(text: String, limit: Int): List<String> {
        if (limit <= 0 || text.length <= limit) return listOf(text)
        val out = ArrayList<String>()
        val buffer = StringBuilder()
        for (sentence in text.split(SENTENCE_END)) {
            if (sentence.isEmpty()) continue
            if (sentence.length > limit) {
                if (buffer.isNotEmpty()) {
                    out.add(buffer.toString())
                    buffer.setLength(0)
                }
                out.addAll(sentence.chunked(limit))
                continue
            }
            if (buffer.length + sentence.length > limit) {
                out.add(buffer.toString())
                buffer.setLength(0)
            }
            buffer.append(sentence)
        }
        if (buffer.isNotEmpty()) out.add(buffer.toString())
        return if (out.isEmpty()) listOf(text) else out
    }

    fun stop() {
        engine?.stop()
        notifySpeaking(false)
    }

    fun shutdown() {
        onStateChanged = null
        onAvailabilityChanged = null
        runCatching { engine?.stop() }
        runCatching { engine?.shutdown() }
        engine = null
        state = State.INIT
    }

    private fun notifySpeaking(speaking: Boolean) {
        speakingFlag = speaking
        onMain { onStateChanged?.invoke(speaking) }
    }

    private fun publish(next: State, text: String) {
        state = next
        detail = text
        onMain { onAvailabilityChanged?.invoke(next, text) }
    }

    /** 回调一律回主线程：界面在里面直接改控件才不会崩 */
    private fun onMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else main.post(block)
    }

    companion object {
        private const val UTTERANCE_ID = "shanghai-memory"
        private const val GOOGLE_TTS = "com.google.android.tts"

        /** 断句点：中英文句末标点与换行，保留标点本身 */
        private val SENTENCE_END = Regex("(?<=[。！？；!?;\\n])")

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
