package com.shanghai.memory.data

import com.shanghai.memory.data.model.RoutePlan
import com.shanghai.memory.data.model.SearchHit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 两条长会话：地点讲述与行程策划。互不干扰，退出界面不丢。 */
enum class ChatChannel(val title: String, val hint: String) {
    STORY("地点讲述", "围绕刚才选中的地点继续追问"),
    PLAN("行程策划", "说说你的偏好：体力、时间、想看的侧重")
}

data class ChatMessage(
    val id: Long,
    val role: Role,
    val text: String = "",
    val streaming: Boolean = false,
    /** 仅流式期间显示的进度说明，例如「正在检索公开资料…」 */
    val stage: String = "",
    val sources: List<SearchHit> = emptyList(),
    val plan: RoutePlan? = null,
    val isError: Boolean = false
) {
    enum class Role { USER, ASSISTANT, SYSTEM }
}

/**
 * 会话内容放在进程内存里，而不是 Activity 的字段里。
 * 「输出不会因为退出聊天而消失」这条要求的实现方式就是：Activity 只是旁观者，
 * 真正写消息的是 Repository，界面重建后从 StateFlow 里把同一份列表读回来。
 * 进程被杀才会丢，这一点在设置页与 README 里都写明。
 */
object ChatStore {

    private val _state = MutableStateFlow<Map<ChatChannel, List<ChatMessage>>>(emptyMap())
    val state: StateFlow<Map<ChatChannel, List<ChatMessage>>> = _state.asStateFlow()

    /**
     * 每条会话是由什么开起来的（哪个地点、哪条路线）。
     * 靠它判断「用户这次是从别的地方进来的」，从而决定是接着聊还是重开——
     * 早先只看会话是否为空，结果是先点过侧栏「聊天」留下一条提示后，
     * 再点「了解更多」就再也不会生成，看起来像 AI 没被触发。
     */
    private val _anchors = MutableStateFlow<Map<ChatChannel, String>>(emptyMap())

    private var nextId = 1L

    fun messages(channel: ChatChannel): List<ChatMessage> = _state.value[channel].orEmpty()

    fun anchorOf(channel: ChatChannel): String? = _anchors.value[channel]

    fun setAnchor(channel: ChatChannel, key: String) {
        _anchors.value = _anchors.value + (channel to key)
    }

    fun lastAssistant(channel: ChatChannel): ChatMessage? =
        messages(channel).lastOrNull { it.role == ChatMessage.Role.ASSISTANT }

    fun add(channel: ChatChannel, message: ChatMessage): Long {
        val id = synchronized(this) { nextId++ }
        val stored = message.copy(id = id)
        synchronized(this) {
            val map = _state.value.toMutableMap()
            map[channel] = map[channel].orEmpty() + stored
            _state.value = map
        }
        return id
    }

    fun update(channel: ChatChannel, id: Long, transform: (ChatMessage) -> ChatMessage) {
        synchronized(this) {
            val list = _state.value[channel].orEmpty()
            val index = list.indexOfFirst { it.id == id }
            if (index < 0) return
            val map = _state.value.toMutableMap()
            map[channel] = list.toMutableList().also { it[index] = transform(it[index]) }
            _state.value = map
        }
    }

    fun appendText(channel: ChatChannel, id: Long, chunk: String) {
        update(channel, id) { it.copy(text = it.text + chunk) }
    }

    fun clear(channel: ChatChannel) {
        synchronized(this) {
            val map = _state.value.toMutableMap()
            map[channel] = emptyList()
            _state.value = map
            _anchors.value = _anchors.value - channel
        }
    }
}

/**
 * 生成任务的宿主作用域。挂在进程上而不是 Activity 上：
 * 用户退出聊天界面时生成继续进行，回来能看到完整结果。
 */
object ChatWorker {
    val scope: CoroutineScope by lazy {
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    }
}
