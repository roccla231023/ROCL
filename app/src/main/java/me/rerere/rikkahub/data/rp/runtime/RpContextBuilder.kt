package me.rerere.rikkahub.data.rp.runtime

import kotlinx.serialization.json.JsonObject
import me.rerere.rikkahub.data.rp.model.RpCard
import me.rerere.rikkahub.data.rp.model.RpCommittedEvent
import me.rerere.rikkahub.data.rp.model.RpModelRole
import me.rerere.rikkahub.data.rp.model.RpOutcome
import me.rerere.rikkahub.data.rp.model.RpSession
import me.rerere.rikkahub.data.rp.model.RpTurn
import me.rerere.rikkahub.utils.JsonInstant

class RpContextBuilder(
    private val stateReducer: RpStateReducer,
) {
    fun buildAdjudication(
        session: RpSession,
        priorTurns: List<RpTurn>,
        action: String,
    ): RpModelRequest = RpModelRequest(
        role = RpModelRole.ADJUDICATOR,
        systemPrompt = buildString {
            appendLine("你是这个互动世界的世界裁决者。")
            appendLine("你的职责是判断玩家行动的结果，不写叙事，不替玩家决定未输入的行动。")
            appendLine("你必须遵守世界设定、规则和当前权威状态。")
            appendLine("只返回 JSON：summary、facts、changes、nextPrompt、needsChoice。")
            appendLine("changes 只能使用状态定义中声明的路径和 SET、APPEND、REMOVE、INCREMENT、DECREMENT 操作。")
            appendCardRules(session.card)
        },
        userPrompt = buildString {
            appendAuthoritativeState(session)
            appendStoryContext(session, priorTurns)
            appendLine("[PLAYER_ACTION]")
            appendLine(action)
        },
    )

    fun buildReview(
        session: RpSession,
        priorTurns: List<RpTurn>,
        action: String,
        proposal: RpOutcome,
    ): RpModelRequest = RpModelRequest(
        role = RpModelRole.REVIEWER,
        systemPrompt = buildString {
            appendLine("你是互动世界的规则审查者。")
            appendLine("只检查裁决提案，不重写提案，不创造新的剧情结果。")
            appendLine("检查它是否违反世界规则、当前状态、状态 Schema，或替玩家擅自做决定。")
            appendLine("只返回 JSON：accepted、confidence、reason、retryable。")
            appendCardRules(session.card)
        },
        userPrompt = buildString {
            appendAuthoritativeState(session)
            appendStoryContext(session, priorTurns)
            appendLine("[PLAYER_ACTION]")
            appendLine(action)
            appendLine("[MODEL_PROPOSAL]")
            appendLine(JsonInstant.encodeToString(proposal))
        },
    )

    fun buildStateKeeping(
        session: RpSession,
        priorTurns: List<RpTurn>,
        action: String,
        proposal: RpOutcome,
    ): RpModelRequest = RpModelRequest(
        role = RpModelRole.STATE_KEEPER,
        systemPrompt = buildString {
            appendLine("你是互动世界的状态记录员。")
            appendLine("你不能重新裁决行动，也不能增加裁决结果之外的事实。")
            appendLine("你只能把已确认的裁决结果整理成状态变更。")
            appendLine("只返回 JSON：summary、facts、changes、nextPrompt、needsChoice。")
            appendLine("changes 只能使用状态定义中声明的路径和 SET、APPEND、REMOVE、INCREMENT、DECREMENT 操作。")
            appendCardRules(session.card)
        },
        userPrompt = buildString {
            appendAuthoritativeState(session)
            appendStoryContext(session, priorTurns)
            appendLine("[PLAYER_ACTION]")
            appendLine(action)
            appendLine("[ACCEPTED_ADJUDICATION]")
            appendLine(JsonInstant.encodeToString(proposal))
        },
    )

    fun buildNarration(
        session: RpSession,
        priorTurns: List<RpTurn>,
        event: RpCommittedEvent,
    ): RpModelRequest = RpModelRequest(
        role = RpModelRole.NARRATOR,
        systemPrompt = buildString {
            appendLine("你是互动故事的叙事者。")
            appendLine("你只能描述已经确认的事件，不得自行增加成功、失败、物品、伤势、人物决定或隐藏信息。")
            appendLine("不要输出 JSON，不要解释裁决过程，不要替玩家决定下一步行动。")
            if (session.card.narrativeStyle.isNotBlank()) {
                appendLine("[NARRATIVE_STYLE]")
                appendLine(session.card.narrativeStyle)
            }
            appendLine("[WORLD_SETTING]")
            appendLine(session.card.worldPrompt)
        },
        userPrompt = buildString {
            appendLine("[PUBLIC_STATE]")
            appendLine(JsonInstant.encodeToString(publicState(session)))
            appendStoryContext(session, priorTurns)
            appendLine("[COMMITTED_EVENT]")
            appendLine(JsonInstant.encodeToString(event.copy(publicState = publicState(session))))
        },
    )

    private fun StringBuilder.appendCardRules(card: RpCard) {
        appendLine("[WORLD_SETTING]")
        appendLine(card.worldPrompt)
        appendLine("[WORLD_RULES]")
        appendLine(card.rulesPrompt)
        appendLine("[STATE_SCHEMA]")
        appendLine(JsonInstant.encodeToString(card.stateSchema))
    }

    private fun StringBuilder.appendAuthoritativeState(session: RpSession) {
        appendLine("[AUTHORITATIVE_STATE]")
        appendLine(JsonInstant.encodeToString(session.state))
        appendLine("[STATE_VERSION]")
        appendLine(session.revision.toString())
    }

    private fun StringBuilder.appendStoryContext(session: RpSession, turns: List<RpTurn>) {
        appendLine("[STORY_MEMORY]")
        appendLine(JsonInstant.encodeToString(session.storyMemory))
        val events = turns.mapNotNull { it.event }.takeLast(MAX_RECENT_EVENTS)
        if (events.isNotEmpty()) {
            appendLine("[RECENT_COMMITTED_EVENTS]")
            events.forEach { appendLine(JsonInstant.encodeToString(it)) }
        }
    }

    private fun publicState(session: RpSession): JsonObject = stateReducer.visibleState(
        state = session.state,
        hiddenPaths = session.card.hiddenStatePaths,
        schema = session.card.stateSchema,
    )

    private companion object {
        const val MAX_RECENT_EVENTS = 8
    }
}

data class RpModelRequest(
    val role: RpModelRole,
    val systemPrompt: String,
    val userPrompt: String,
)
