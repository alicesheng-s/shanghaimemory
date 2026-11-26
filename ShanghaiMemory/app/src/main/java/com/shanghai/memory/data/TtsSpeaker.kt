package com.shanghai.memory.data

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.shanghai.memory.ui.theme.ThemeState
import java.util.Locale

/**
 * 系统 TTS 朗读。支持中英双语切换。
 */
class TtsSpeaker(context: Context) {

    enum class State { INIT, READY, NO_ENGINE, LANG_MISSING, FAILED }

    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private var engine: TextToSpeech? = null
    private var pending: String? = null
    private var pendingSpeed = 1.0f
    private var speakingFlag = false
    private var generation = 0

    private val en: Boolean get() = ThemeState.isEnglish

    @Volatile
    var state: State = State.INIT
        private set

    @Volatile
    var detail: String = if (en) "Waking up voice engine…" else "正在唤醒语音引擎…"
        private set

    var onStateChanged: ((Boolean) -> Unit)? = null
    var onAvailabilityChanged: ((State, String) -> Unit)? = null

    val isSpeaking: Boolean get() = speakingFlag

    init {
        start(null)
    }

    private fun start(forceEngine: String?) {
        val myGeneration = ++generation
        val listener = TextToSpeech.OnInitListener { status ->
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
        if (forceEngine == null && hasGoogleEngine(appContext)) {
            publish(
                State.INIT,
                if (en) "Default engine unavailable, trying Google TTS…"
                else "系统默认语音引擎没起来，正在切到 Google 语音服务…"
            )
            runCatching { engine?.shutdown() }
            engine = null
            start(GOOGLE_TTS)
        } else {
            publish(
                State.NO_ENGINE,
                if (en) "Voice engine failed (code $status). Pick another engine in system Text-to-Speech settings."
                else "语音引擎没能启动（错误码 $status），可以去系统「文字转语音」设置里换个引擎试试。"
            )
        }
    }

    private fun applyLanguage() {
        val e = engine ?: return
        val targetLocale = if (en) Locale.US else Locale.SIMPLIFIED_CHINESE
        var available = e.isLanguageAvailable(targetLocale)
        val chosen = if (en && (available == TextToSpeech.LANG_MISSING_DATA || available == TextToSpeech.LANG_NOT_SUPPORTED)) {
            available = e.isLanguageAvailable(Locale.ENGLISH)
            Locale.ENGLISH
        } else {
            targetLocale
        }
        if (available == TextToSpeech.LANG_MISSING_DATA || available == TextToSpeech.LANG_NOT_SUPPORTED) {
            publish(
                State.LANG_MISSING,
                if (en) "${engineName()} is missing an English voice pack. Download it in system TTS settings."
                else "当前引擎（${engineName()}）缺少中文语音包，请到系统「文字转语音」设置里下载。"
            )
            return
        }
        val result = e.setLanguage(chosen)
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            publish(
                State.LANG_MISSING,
                if (en) "${engineName()} couldn't switch to English."
                else "引擎没法切换到中文（${engineName()}）。"
            )
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
        publish(
            State.READY,
            if (en) "Voice ready: ${engineName()}" else "语音已就绪：${engineName()}"
        )
        pending?.let { text ->
            pending = null
            speak(text, pendingSpeed)
        }
    }

    private fun engineName(): String {
        val e = engine ?: return if (en) "Unknown engine" else "未知引擎"
        val default = runCatching { e.defaultEngine }.getOrNull().orEmpty()
        val listed = runCatching {
            e.engines.firstOrNull { it.name == default } ?: e.engines.firstOrNull()
        }.getOrNull()
        if (listed != null && listed.label.isNotBlank()) return listed.label
        if (default.isBlank()) return if (en) "System default" else "系统默认引擎"
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
            publish(
                State.NO_ENGINE,
                if (en) "Voice engine isn't ready yet." else "语音引擎没起来，暂时读不了。"
            )
            return
        }
        if (state == State.LANG_MISSING) return
        if (state == State.INIT) {
            pending = body
            pendingSpeed = speed
            return
        }
        e.setSpeechRate(speed.coerceIn(0.4f, 2.2f))
        val chunks = splitForTts(body, TextToSpeech.getMaxSpeechInputLength())
        var rejected = false
        chunks.forEachIndexed { index, chunk ->
            val mode = if (index == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
            val code = e.speak(chunk, mode, Bundle(), "$UTTERANCE_ID-$index")
            if (code == TextToSpeech.ERROR) rejected = true
        }
        if (rejected) {
            publish(
                State.FAILED,
                if (en) "${engineName()} declined to speak—voice data may be missing."
                else "引擎拒绝朗读（${engineName()}），可能缺语音数据包。"
            )
        }
    }

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

    private fun onMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else main.post(block)
    }

    companion object {
        private const val UTTERANCE_ID = "shanghai-memory"
        private const val GOOGLE_TTS = "com.google.android.tts"
        private val SENTENCE_END = Regex("(?<=[。！？；.!?;\\n])")

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
