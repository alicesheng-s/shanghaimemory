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

    /** 一站都还没到过时的游标 */
    private const val NOT_STARTED = -1

    private val _plan = MutableStateFlow<RoutePlan?>(null)
    val plan: StateFlow<RoutePlan?> = _plan.asStateFlow()

    /** 已经走到第几站（0 起算）；-1 表示还没到过任何一站 */
    private val _visited = MutableStateFlow(NOT_STARTED)
    val visited: StateFlow<Int> = _visited.asStateFlow()

    fun set(plan: RoutePlan?) {
        _plan.value = plan
        _visited.value = NOT_STARTED
    }

    fun current(): RoutePlan? = _plan.value

    /**
     * 记下到过的站。只向前推进：走回已经路过的旧站时不必重播提示。
     * 没有路线或站数为 0 时直接忽略，免得把游标写成野值。
     */
    fun markVisited(index: Int) {
        val size = _plan.value?.stops?.size ?: return
        if (size <= 0) return
        val target = index.coerceIn(0, size - 1)
        if (target > _visited.value) _visited.value = target
    }

    /**
     * 下一站的下标。全部走完后返回 -1，调用方用 getOrNull 拿到 null 就判定收尾。
     * 不能用 coerceAtMost(size - 1)：那样游标会锁死在末站，每刷新一次都在原地重复提示。
     */
    fun nextIndex(): Int {
        val size = _plan.value?.stops?.size ?: return NOT_STARTED
        if (size <= 0) return NOT_STARTED
        val next = _visited.value + 1
        return if (next in 0 until size) next else NOT_STARTED
    }

    fun clear() {
        _plan.value = null
        _visited.value = NOT_STARTED
    }
}
