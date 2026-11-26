package com.shanghai.memory.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.tabs.TabLayout
import com.shanghai.memory.R
import com.shanghai.memory.data.AmapService
import com.shanghai.memory.data.AppSettings
import com.shanghai.memory.data.ChatChannel
import com.shanghai.memory.data.ChatMessage
import com.shanghai.memory.data.ChatStore
import com.shanghai.memory.data.ChatWorker
import com.shanghai.memory.data.PromptTemplates
import com.shanghai.memory.data.RouteRepository
import com.shanghai.memory.data.RouteSession
import com.shanghai.memory.data.SettingsStore
import com.shanghai.memory.data.StoryEvent
import com.shanghai.memory.data.StoryRepository
import com.shanghai.memory.data.TtsSpeaker
import com.shanghai.memory.data.llm.ChatClient
import com.shanghai.memory.data.llm.Turn
import com.shanghai.memory.data.model.PoiInfo
import com.shanghai.memory.data.model.RoutePlan
import com.shanghai.memory.data.model.StoryPhase
import com.shanghai.memory.databinding.ActivityChatBinding
import com.shanghai.memory.ui.theme.ThemedActivity
import com.shanghai.memory.ui.theme.colorOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 两条长会话的界面。它只是 [ChatStore] 的旁观者：
 * 消息由 Repository 写进 Store，界面重建后照旧读出来。
 */
class ChatActivity : ThemedActivity() {

    companion object {
        private const val EXTRA_CHANNEL = "extra_channel"
        private const val EXTRA_POI_NAME = "extra_poi_name"
        private const val EXTRA_POI_TYPE = "extra_poi_type"
        private const val EXTRA_POI_ADDRESS = "extra_poi_address"
        private const val EXTRA_POI_DISTRICT = "extra_poi_district"
        private const val EXTRA_POI_LAT = "extra_poi_lat"
        private const val EXTRA_POI_LNG = "extra_poi_lng"
        private const val EXTRA_PLAN_NAME = "extra_plan_name"
        private const val EXTRA_PLAN_STOPS = "extra_plan_stops"

        fun storyIntent(context: Context, poi: PoiInfo): Intent =
            Intent(context, ChatActivity::class.java).apply {
                putExtra(EXTRA_CHANNEL, ChatChannel.STORY.name)
                putExtra(EXTRA_POI_NAME, poi.name)
                putExtra(EXTRA_POI_TYPE, poi.type)
                putExtra(EXTRA_POI_ADDRESS, poi.address)
                putExtra(EXTRA_POI_DISTRICT, poi.district)
                putExtra(EXTRA_POI_LAT, poi.lat)
                putExtra(EXTRA_POI_LNG, poi.lng)
            }

        fun planIntent(context: Context, plan: RoutePlan? = null): Intent =
            Intent(context, ChatActivity::class.java).apply {
                putExtra(EXTRA_CHANNEL, ChatChannel.PLAN.name)
                if (plan != null) {
                    putExtra(EXTRA_PLAN_NAME, plan.routeName)
                    putStringArrayListExtra(EXTRA_PLAN_STOPS, ArrayList(plan.stops.map { it.name }))
                }
            }

        /** 用户自己写的主题：只带名字与参考站点，其余交给对话。 */
        fun planIntent(
            context: Context,
            routeName: String?,
            stops: List<String>
        ): Intent =
            Intent(context, ChatActivity::class.java).apply {
                putExtra(EXTRA_CHANNEL, ChatChannel.PLAN.name)
                if (!routeName.isNullOrBlank()) putExtra(EXTRA_PLAN_NAME, routeName)
                putStringArrayListExtra(EXTRA_PLAN_STOPS, ArrayList(stops))
            }

        fun open(context: Context): Intent =
            Intent(context, ChatActivity::class.java)
                .putExtra(EXTRA_CHANNEL, ChatChannel.STORY.name)
    }

    private lateinit var binding: ActivityChatBinding
    private lateinit var store: SettingsStore
    private lateinit var amap: AmapService
    private val adapter = ChatAdapter()

    private var channel = ChatChannel.STORY
    private var poi: PoiInfo? = null
    private var planName: String = ""
    private var planStops: List<String> = emptyList()

    private var tts: TtsSpeaker? = null
    private var ttsEnabled = false
    private var ttsSpeed = 1.0f
    private var spokenId = -1L
    /** 朗读不可用的提示只弹一次，别每条消息都弹 */
    private var ttsWarned = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChatBinding.inflate(layoutInflater)
        setContentView(binding.root)

        store = SettingsStore(this)
        amap = AmapService(this)

        channel = intent.getStringExtra(EXTRA_CHANNEL)
            ?.let { runCatching { ChatChannel.valueOf(it) }.getOrNull() }
            ?: ChatChannel.STORY
        poi = readPoi(intent)
        planName = intent.getStringExtra(EXTRA_PLAN_NAME).orEmpty()
        planStops = intent.getStringArrayListExtra(EXTRA_PLAN_STOPS).orEmpty()

        binding.list.layoutManager = LinearLayoutManager(this)
        binding.list.adapter = adapter

        setupTabs()
        binding.btnBack.setOnClickListener { finish() }
        binding.btnClear.setOnClickListener { confirmClear() }
        binding.btnSend.setOnClickListener { onSend() }

        observe()
        bootstrap()
    }

    override fun onDestroy() {
        tts?.shutdown()
        tts = null
        super.onDestroy()
    }

    // ---- 界面骨架 ----

    private fun setupTabs() {
        val tabs = binding.tabs
        ChatChannel.entries.forEach { item ->
            tabs.addTab(tabs.newTab().setText(item.title).setTag(item))
        }
        tabs.getTabAt(ChatChannel.entries.indexOf(channel))?.select()
        tabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                val picked = tab.tag as? ChatChannel ?: return
                switchTo(picked)
            }

            override fun onTabUnselected(tab: TabLayout.Tab) = Unit
            override fun onTabReselected(tab: TabLayout.Tab) = Unit
        })
        updateHeader()
    }

    private fun switchTo(picked: ChatChannel) {
        if (picked == channel) return
        channel = picked
        spokenId = ChatStore.messages(channel)
            .lastOrNull { it.role == ChatMessage.Role.ASSISTANT && !it.streaming }
            ?.id ?: -1L
        updateHeader()
        render(ChatStore.messages(channel))
        bootstrap()
    }

    private fun updateHeader() {
        // 标题行已经占了位置，Tab 上还有同样的字；这里改成更具体的对象名，避免两行说同一句话
        binding.txtTitle.text = when {
            channel == ChatChannel.STORY && poi != null -> poi!!.name
            channel == ChatChannel.PLAN && !planName.isNullOrBlank() -> planName
            else -> channel.title
        }
        binding.etInput.hint = channel.hint
    }

    private fun observe() {
        lifecycleScope.launch {
            ChatStore.state.collect { map ->
                render(map[channel].orEmpty())
            }
        }
    }

    private fun render(list: List<ChatMessage>) {
        adapter.submitList(list) {
            if (list.isNotEmpty()) binding.list.scrollToPosition(list.size - 1)
        }
        maybeSpeak(list.lastOrNull())
    }

    private fun maybeSpeak(last: ChatMessage?) {
        if (!ttsEnabled || last == null) return
        if (last.role != ChatMessage.Role.ASSISTANT) return
        if (last.streaming || last.text.isBlank() || last.id == spokenId) return
        spokenId = last.id
        val speaker = ensureSpeaker()
        speaker.speak(last.text, ttsSpeed)
    }

    /**
     * 惰性建朗读器，并挂上可用性回调。
     * 厂商引擎经常「调用成功但没声音」，所以一旦不可用就明确告诉用户，
     * 而不是让人对着没有反应的界面猜。
     */
    private fun ensureSpeaker(): TtsSpeaker {
        tts?.let { return it }
        val speaker = TtsSpeaker(applicationContext)
        speaker.onAvailabilityChanged = { state, detail ->
            runOnUiThread {
                val broken = state == TtsSpeaker.State.NO_ENGINE ||
                    state == TtsSpeaker.State.LANG_MISSING ||
                    state == TtsSpeaker.State.FAILED
                if (broken && !ttsWarned && ttsEnabled) {
                    ttsWarned = true
                    Toast.makeText(this, detail, Toast.LENGTH_LONG).show()
                }
            }
        }
        tts = speaker
        return speaker
    }

    // ---- 开场 ----

    private fun bootstrap() {
        lifecycleScope.launch {
            val key = anchorKey()
            val anchor = ChatStore.anchorOf(channel)
            val existing = ChatStore.messages(channel).isNotEmpty()

            // 从另一个地点/路线进来就换一条新会话；没有具体锚点（比如从侧栏点进来的"聊天"）
            // 则保留已有内容，不能把上一轮讲好的东西清掉
            if (existing && key.isNotBlank() && anchor != key) {
                ChatStore.clear(channel)
                spokenId = -1L
            } else if (existing) {
                // 回到已有对话（切 tab 或重新打开）时不自动重读已经讲完的旧消息
                spokenId = ChatStore.messages(channel)
                    .lastOrNull { it.role == ChatMessage.Role.ASSISTANT && !it.streaming }
                    ?.id ?: -1L
            }
            ChatStore.setAnchor(channel, key)

            val settings = store.current()
            ttsEnabled = settings.ttsEnabled
            ttsSpeed = settings.ttsSpeed
            if (ttsEnabled) ensureSpeaker()
            updateDiag(settings)

            if (ChatStore.messages(channel).isEmpty()) {
                when (channel) {
                    ChatChannel.STORY -> startStory()
                    ChatChannel.PLAN -> openPlan(settings)
                }
            }
        }
    }

    /**
     * 这次进入会话的锚点：讲某个地点 / 聊某条路线。
     * 锚点相同就接着上一轮聊，不同就重开——空串表示"没有具体对象"。
     */
    private fun anchorKey(): String = when (channel) {
        ChatChannel.STORY -> poi?.let { "poi:${it.name}" }.orEmpty()
        ChatChannel.PLAN -> when {
            planName.isNotBlank() -> "plan:$planName|${planStops.joinToString(",")}"
            planStops.isNotEmpty() -> "plan:|${planStops.joinToString(",")}"
            else -> ""
        }
    }

    /** 顶部一行的自检信息：用的哪个服务商、哪个模型、有没有 Key、联网开没开 */
    private fun updateDiag(settings: AppSettings) {
        val en = com.shanghai.memory.ui.theme.ThemeState.isEnglish
        val keyState = if (settings.hasKey) {
            getString(R.string.chat_diag_ready)
        } else {
            getString(R.string.chat_diag_nokey)
        }
        val search = when {
            en && settings.canSearch -> "Web search on"
            en -> "Web search off"
            settings.canSearch -> "联网查证开启"
            else -> "联网查证关闭"
        }
        binding.txtDiag.text = listOf(
            settings.provider.displayName,
            settings.model.ifBlank { if (en) "No model" else "未选模型" },
            keyState,
            search
        ).joinToString(" · ")
        binding.txtDiag.setTextColor(
            colorOf(if (settings.hasKey) R.attr.shTextMuted else R.attr.shDanger)
        )
    }

    // ---- 等待中的计时显示 ----
    // 首字出来之前只有一句静态文案，用户分不清是在跑还是卡死；
    // 这里每 400ms 把已等待秒数写进 stage，配合 ChatAdapter 的占位正文显示。

    private val tickers = mutableMapOf<Long, Job>()
    private val stageBases = mutableMapOf<Long, String>()

    private fun beginStage(id: Long, base: String) {
        val owner = channel
        stageBases[id] = base
        tickers.remove(id)?.cancel()
        val startedAt = System.currentTimeMillis()
        tickers[id] = ChatWorker.scope.launch {
            while (isActive) {
                val current = stageBases[id]
                if (current.isNullOrBlank()) break
                val sec = (System.currentTimeMillis() - startedAt) / 1000
                ChatStore.update(owner, id) { it.copy(stage = "$current  ${sec}s") }
                delay(400)
            }
        }
    }

    private fun updateStageBase(id: Long, base: String) {
        if (stageBases.containsKey(id)) stageBases[id] = base
    }

    private fun endStage(id: Long) {
        tickers.remove(id)?.cancel()
        stageBases.remove(id)
    }

    private fun startStory() {
        val en = com.shanghai.memory.ui.theme.ThemeState.isEnglish
        val place = poi
        if (place == null) {
            ChatStore.add(
                channel,
                ChatMessage(
                    0,
                    ChatMessage.Role.SYSTEM,
                    if (en) "No landmark selected yet. Head back to the map, rest the pin over a place, and tap \"Hear Its Story\"."
                    else "还没选好地点呢。回地图把准星停在一处老地方，再点「听听它的故事」吧。"
                )
            )
            return
        }
        val owner = channel
        ChatWorker.scope.launch {
            val settings = store.current()
            ChatStore.add(owner, ChatMessage(0, ChatMessage.Role.SYSTEM, guidance(settings)))
            ChatStore.add(
                owner,
                ChatMessage(0, ChatMessage.Role.USER, if (en) "Tell me about ${place.name}" else "讲讲这里：${place.name}")
            )
            val initStage = if (en) "Checking public sources…" else "正在翻找公开资料…"
            val id = ChatStore.add(
                owner,
                ChatMessage(0, ChatMessage.Role.ASSISTANT, streaming = true, stage = initStage)
            )
            beginStage(id, initStage)
            var firstChunk = true
            try {
                StoryRepository(store).story(place).collect { event ->
                    when (event) {
                        is StoryEvent.Phase -> {
                            val base = stageOf(event.phase)
                            updateStageBase(id, base)
                            ChatStore.update(owner, id) { it.copy(stage = base) }
                        }

                        is StoryEvent.Sources -> ChatStore.update(owner, id) {
                            it.copy(sources = event.hits)
                        }

                        is StoryEvent.Reasoning -> {
                            val tailText = event.text.trim().replace('\n', ' ').takeLast(36)
                            if (tailText.isNotBlank()) {
                                updateStageBase(id, if (en) "Thinking: $tailText" else "整理思路中：$tailText")
                            }
                        }

                        is StoryEvent.Delta -> {
                            if (firstChunk) {
                                firstChunk = false
                                endStage(id)
                                ChatStore.update(owner, id) { it.copy(stage = "") }
                            }
                            ChatStore.appendText(owner, id, event.text)
                        }

                        is StoryEvent.Curated -> {
                            endStage(id)
                            ChatStore.update(owner, id) {
                                it.copy(
                                    text = it.text.ifBlank { event.text },
                                    sources = emptyList(),
                                    stage = event.reason,
                                    streaming = false
                                )
                            }
                        }

                        is StoryEvent.Done -> {
                            endStage(id)
                            ChatStore.update(owner, id) {
                                it.copy(streaming = false, stage = "")
                            }
                        }

                        is StoryEvent.Failed -> {
                            endStage(id)
                            ChatStore.update(owner, id) {
                                it.copy(
                                    text = if (it.text.isBlank()) event.message else it.text + "\n\n" + event.message,
                                    streaming = false,
                                    stage = "",
                                    isError = true
                                )
                            }
                        }
                    }
                }
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                endStage(id)
                ChatStore.update(owner, id) {
                    it.copy(
                        text = it.text.ifBlank {
                            if (en) "Story failed: ${t.message}" else "没能讲成：${t.message}"
                        },
                        streaming = false,
                        stage = "",
                        isError = true
                    )
                }
            }
        }
    }

    private fun openPlan(settings: AppSettings) {
        val en = com.shanghai.memory.ui.theme.ThemeState.isEnglish
        ChatStore.add(channel, ChatMessage(0, ChatMessage.Role.SYSTEM, guidance(settings)))
        val name = planName.ifBlank { if (en) "Shanghai City Walk" else "上海城市漫游" }
        ChatStore.add(
            channel,
            ChatMessage(0, ChatMessage.Role.ASSISTANT, PromptTemplates.planOpener(name, planStops))
        )
    }

    private fun guidance(settings: AppSettings): String = when {
        settings.canSearch -> getString(R.string.chat_guidance_on)
        settings.hasKey -> getString(R.string.chat_guidance_off)
        else -> getString(R.string.chat_guidance_nokey)
    }

    private fun stageOf(phase: StoryPhase): String {
        val en = com.shanghai.memory.ui.theme.ThemeState.isEnglish
        return when (phase) {
            StoryPhase.IDLE -> ""
            StoryPhase.SEARCHING -> if (en) "Searching public records…" else "正在翻找公开资料…"
            StoryPhase.GENERATING -> if (en) "Writing the story…" else "正在为你讲述…"
            StoryPhase.DONE -> ""
            StoryPhase.FAILED -> if (en) "Interrupted" else "生成中断了"
            StoryPhase.OFFLINE_CURATED -> if (en) "Using offline curated notes…" else "改用离线策展故事…"
        }
    }

    // ---- 用户输入 ----

    private fun onSend() {
        val text = binding.etInput.text?.toString()?.trim().orEmpty()
        if (text.isEmpty()) return
        binding.etInput.setText("")
        when (channel) {
            ChatChannel.PLAN -> replan(text)
            ChatChannel.STORY -> followUp(text)
        }
    }

    private fun followUp(question: String) {
        val en = com.shanghai.memory.ui.theme.ThemeState.isEnglish
        val place = poi
        if (place == null) {
            ChatStore.add(
                channel,
                ChatMessage(
                    0,
                    ChatMessage.Role.SYSTEM,
                    if (en) "No landmark selected yet—pick a place on the map first."
                    else "还没选中地点呢，先回地图挑一处老地方吧。"
                )
            )
            return
        }
        val owner = channel
        val history = historyTurns()
        val draft = draftText()
        ChatStore.add(owner, ChatMessage(0, ChatMessage.Role.USER, question))
        val stageText = if (en) "Thinking up a reply…" else "正在组织回答…"
        val id = ChatStore.add(
            owner,
            ChatMessage(0, ChatMessage.Role.ASSISTANT, streaming = true, stage = stageText)
        )
        beginStage(id, stageText)
        ChatWorker.scope.launch {
            val settings = store.current()
            if (!settings.hasKey) {
                endStage(id)
                ChatStore.update(owner, id) {
                    it.copy(
                        text = if (en) "No API Key yet—add one in Settings to chat freely." else "还没填 API Key，暂时没法继续追问。去设置里填一个再来聊吧。",
                        streaming = false,
                        stage = "",
                        isError = true
                    )
                }
                return@launch
            }
            var firstChunk = true
            try {
                ChatClient().stream(
                    settings = settings,
                    systemPrompt = PromptTemplates.chatSystem(),
                    userPrompt = PromptTemplates.followUpUserPrompt(place, draft, question),
                    history = history,
                    onReasoning = { piece ->
                        val tailText = piece.trim().replace('\n', ' ').takeLast(36)
                        if (tailText.isNotBlank()) {
                            updateStageBase(id, if (en) "Thinking: $tailText" else "整理思路中：$tailText")
                        }
                    }
                ).collect { chunk ->
                    if (firstChunk) {
                        firstChunk = false
                        endStage(id)
                        ChatStore.update(owner, id) { it.copy(stage = "") }
                    }
                    ChatStore.appendText(owner, id, chunk)
                }
                ChatStore.update(owner, id) { it.copy(streaming = false, stage = "") }
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                endStage(id)
                ChatStore.update(owner, id) {
                    it.copy(
                        text = it.text.ifBlank { if (en) "Request failed: ${t.message}" else "刚才请求失败了：${t.message}" },
                        streaming = false,
                        stage = "",
                        isError = true
                    )
                }
            }
        }
    }

    private fun replan(notes: String) {
        val en = com.shanghai.memory.ui.theme.ThemeState.isEnglish
        val name = planName.ifBlank { if (en) "Shanghai City Walk" else "上海城市漫游" }
        val owner = channel
        ChatStore.add(owner, ChatMessage(0, ChatMessage.Role.USER, notes))
        val stageText = if (en) "Arranging stops & pacing…" else "正在为你排路线和停留时间…"
        val id = ChatStore.add(
            owner,
            ChatMessage(0, ChatMessage.Role.ASSISTANT, streaming = true, stage = stageText)
        )
        beginStage(id, stageText)
        ChatWorker.scope.launch {
            try {
                val repo = RouteRepository(store, amap = amap)
                val raw = repo.generate(name, null, notes, planStops)
                val plan = repo.locate(raw)
                RouteSession.set(plan)
                endStage(id)
                ChatStore.update(owner, id) {
                    it.copy(text = renderPlan(plan), streaming = false, stage = "", plan = plan)
                }
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                endStage(id)
                ChatStore.update(owner, id) {
                    it.copy(
                        text = it.text.ifBlank {
                            if (en) "Couldn't build this route: ${t.message}" else "这条路线没排出来：${t.message}"
                        },
                        streaming = false,
                        stage = "",
                        isError = true
                    )
                }
            }
        }
    }

    private fun renderPlan(plan: RoutePlan): String {
        val en = com.shanghai.memory.ui.theme.ThemeState.isEnglish
        return buildString {
            appendLine("**${plan.routeName}**")
            if (en) {
                appendLine("Theme: ${plan.theme} · ~${plan.totalMin} min total")
            } else {
                appendLine("主题：${plan.theme}　全程约 ${plan.totalMin} 分钟")
            }
            plan.stops.forEach { stop ->
                appendLine()
                if (en) {
                    appendLine("**Stop ${stop.seq} · ${stop.name}**")
                    append("Stay ~${stop.stayMin} min")
                } else {
                    appendLine("**第 ${stop.seq} 站　${stop.name}**")
                    append("建议逛 ${stop.stayMin} 分钟")
                }
                if (stop.area.isNotBlank()) append(" · ${stop.area}")
                appendLine()
                if (stop.reason.isNotBlank()) {
                    appendLine(if (en) "Why go: ${stop.reason}" else "为什么去：${stop.reason}")
                }
                if (stop.storyHook.isNotBlank()) {
                    appendLine(if (en) "Story hook: ${stop.storyHook}" else "现场切口：${stop.storyHook}")
                }
            }
            if (plan.transitionLogic.isNotBlank()) {
                appendLine()
                appendLine(if (en) "Getting around: ${plan.transitionLogic}" else "怎么顺路走：${plan.transitionLogic}")
            }
            appendLine()
            append(
                if (en) "Your route is plotted on the map—head back and turn on Live Navigation in the side menu to follow along."
                else "路线已经画到地图上啦，回地图可以在侧栏打开「实时位置导航」，跟着指引一站一站逛。"
            )
        }
    }

    private fun historyTurns(): List<Turn> =
        ChatStore.messages(channel)
            .filter { it.role != ChatMessage.Role.SYSTEM && !it.streaming && it.text.isNotBlank() }
            .takeLast(8)
            .map {
                Turn(
                    role = if (it.role == ChatMessage.Role.USER) "user" else "assistant",
                    content = it.text.take(1200)
                )
            }

    private fun draftText(): String =
        ChatStore.messages(channel)
            .lastOrNull { it.role == ChatMessage.Role.ASSISTANT && !it.streaming && it.text.isNotBlank() }
            ?.text
            .orEmpty()

    private fun confirmClear() {
        val en = com.shanghai.memory.ui.theme.ThemeState.isEnglish
        MaterialAlertDialogBuilder(this)
            .setMessage(
                if (en) "Clear \"${channel.title}\" chat history?"
                else "要清空「${channel.title}」的聊天记录吗？"
            )
            .setPositiveButton(R.string.chat_clear) { _, _ ->
                ChatStore.clear(channel)
                spokenId = -1L
                render(emptyList())
                bootstrap()
            }
            .setNegativeButton(R.string.common_cancel, null)
            .show()
    }

    private fun readPoi(intent: Intent): PoiInfo? {
        val name = intent.getStringExtra(EXTRA_POI_NAME) ?: return null
        return PoiInfo(
            name = name,
            type = intent.getStringExtra(EXTRA_POI_TYPE).orEmpty(),
            address = intent.getStringExtra(EXTRA_POI_ADDRESS).orEmpty(),
            district = intent.getStringExtra(EXTRA_POI_DISTRICT).orEmpty(),
            lat = intent.getDoubleExtra(EXTRA_POI_LAT, 0.0),
            lng = intent.getDoubleExtra(EXTRA_POI_LNG, 0.0)
        )
    }
}
