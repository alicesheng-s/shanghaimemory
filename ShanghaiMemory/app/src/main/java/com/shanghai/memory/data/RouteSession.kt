package com.shanghai.memory.data

import com.shanghai.memory.data.model.RoutePlan
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 当前正在游览的路线。策划通道生成方案后写进来，地图页订阅它去画线和做向导。
 * 与 ChatStore 同理放在进程内存：切换页面不丢，进程结束即清空。
 */
object RouteSession {

    private val _plan = MutableStateFlow<RoutePlan?>(null)
    val plan: StateFlow<RoutePlan?> = _plan.asStateFlow()

    private val _visited = MutableStateFlow(0)
    /** 已经走到第几站（0 起算） */
    val visited: StateFlow<Int> = _visited.asStateFlow()

    fun set(plan: RoutePlan?) {
        _plan.value = plan
        _visited.value = 0
    }

    fun current(): RoutePlan? = _plan.value

    fun markVisited(index: Int) {
        _visited.value = index.coerceAtLeast(0)
    }

    fun nextIndex(): Int {
        val size = _plan.value?.stops?.size ?: return 0
        return (_visited.value + 1).coerceAtMost(size - 1)
    }

    fun clear() {
        _plan.value = null
        _visited.value = 0
    }
}
