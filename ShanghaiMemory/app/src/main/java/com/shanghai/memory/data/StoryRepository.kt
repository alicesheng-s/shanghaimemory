package com.shanghai.memory.data

import com.shanghai.memory.data.llm.ChatClient
import com.shanghai.memory.data.llm.SearchClient
import com.shanghai.memory.data.model.PoiInfo
import com.shanghai.memory.data.model.SearchHit
import com.shanghai.memory.data.model.StoryPhase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow

sealed interface StoryEvent {
    data class Phase(val phase: StoryPhase) : StoryEvent
    data class Sources(val hits: List<SearchHit>) : StoryEvent
    data class Delta(val text: String) : StoryEvent

    /** 有的模型会强制开启思考，把思考片段抛出来给界面做进度显示 */
    data class Reasoning(val text: String) : StoryEvent
    data class Curated(val text: String, val reason: String) : StoryEvent
    data object Done : StoryEvent
    data class Failed(val message: String) : StoryEvent
}

/**
 * 检索 → 生成 → 兜底 三段。任何一段失败都退到下一档，
 * 保证「站在这里，一定有话可说」这一体验下限。
 */
class StoryRepository(
    private val settingsStore: SettingsStore,
    private val chat: ChatClient = ChatClient(),
    private val search: SearchClient = SearchClient()
) {

    fun story(poi: PoiInfo): Flow<StoryEvent> = channelFlow {
        val settings = settingsStore.current()
        val parentName = if (settings.expandParentContext) PlaceContext.broaden(poi.name) else null
        val searchSubject = parentName ?: poi.name

        if (!settings.hasKey) {
            send(StoryEvent.Phase(StoryPhase.OFFLINE_CURATED))
            val reason = if (com.shanghai.memory.ui.theme.ThemeState.isEnglish) {
                "No API Key yet — showing offline curated notes"
            } else {
                "还没填 API Key，先为你展示离线策展故事"
            }
            send(StoryEvent.Curated(CuratedStories.forPoi(poi, settings.expandParentContext), reason))
            return@channelFlow
        }

        val buffer = StringBuilder()
        var hits: List<SearchHit> = emptyList()

        try {
            if (settings.canSearch) {
                send(StoryEvent.Phase(StoryPhase.SEARCHING))
                hits = gatherSources(settings, searchSubject, poi.district)
                if (hits.isNotEmpty()) send(StoryEvent.Sources(hits))
            }

            send(StoryEvent.Phase(StoryPhase.GENERATING))
            val userPrompt = if (hits.isNotEmpty()) {
                PromptTemplates.storyUserPrompt(poi, hits, parentName, settings.expandParentContext)
            } else {
                PromptTemplates.storyUserPromptFallback(poi, parentName, settings.expandParentContext)
            }

            chat.stream(
                settings = settings,
                systemPrompt = PromptTemplates.storySystem(),
                userPrompt = userPrompt,
                onReasoning = { piece -> trySend(StoryEvent.Reasoning(piece)) }
            ).collect { chunk ->
                buffer.append(chunk)
                send(StoryEvent.Delta(chunk))
            }
            send(StoryEvent.Phase(StoryPhase.DONE))
            send(StoryEvent.Done)
        } catch (c: CancellationException) {
            throw c
        } catch (t: Throwable) {
            val en = com.shanghai.memory.ui.theme.ThemeState.isEnglish
            if (buffer.isEmpty()) {
                send(StoryEvent.Phase(StoryPhase.OFFLINE_CURATED))
                val why = t.message ?: if (en) "network issue" else "网络不太稳"
                val reason = if (en) {
                    "Generation failed ($why) — switched to offline curated notes"
                } else {
                    "刚才没连上（$why），先切换成离线策展故事"
                }
                send(StoryEvent.Curated(CuratedStories.forPoi(poi, settings.expandParentContext), reason))
            } else {
                send(StoryEvent.Phase(StoryPhase.FAILED))
                send(
                    StoryEvent.Failed(
                        if (en) "Connection interrupted; kept what was generated so far."
                        else "网络突然断开了，前面的内容已经为你留着。"
                    )
                )
            }
        }
    }

    /** 并行发起「历史由来」与「官方公众号/官媒近期活动」双路检索，合并去重后喂给模型 */
    private suspend fun gatherSources(
        settings: AppSettings,
        subject: String,
        district: String
    ): List<SearchHit> = coroutineScope {
        val historyJob = async {
            search.search(
                settings = settings,
                query = TopicRouting.storyHistoryQuery(subject, district),
                count = 5,
                topic = TopicRouting.Topic.GENERAL
            )
        }
        val officialJob = async {
            val primary = search.search(
                settings = settings,
                query = TopicRouting.officialActivityQuery(subject),
                count = 5,
                topic = TopicRouting.Topic.OFFICIAL
            )
            primary.ifEmpty {
                search.search(
                    settings = settings,
                    query = TopicRouting.recentActivityQuery(subject),
                    count = 4,
                    topic = TopicRouting.Topic.GENERAL
                )
            }
        }
        val histHits = historyJob.await()
        val officialHits = officialJob.await()
        (histHits.take(4) + officialHits.take(4)).distinctBy { it.url }.take(8)
    }
}
