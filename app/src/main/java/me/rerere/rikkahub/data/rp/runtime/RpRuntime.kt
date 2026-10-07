package me.rerere.rikkahub.data.rp.runtime

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.rerere.ai.provider.Model
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.findModelById
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.MessageNode
import me.rerere.rikkahub.data.rp.model.RpCard
import me.rerere.rikkahub.data.rp.model.RpOutcome
import me.rerere.rikkahub.data.rp.model.RpReview
import me.rerere.rikkahub.data.rp.model.RpSession
import me.rerere.rikkahub.data.rp.model.RpSessionSnapshot
import me.rerere.rikkahub.data.rp.model.RpStateChange
import me.rerere.rikkahub.data.rp.model.RpTurn
import me.rerere.rikkahub.data.rp.model.RpTurnStatus
import me.rerere.rikkahub.data.rp.model.hasAdjudication
import me.rerere.rikkahub.data.repository.ConversationRepository
import me.rerere.rikkahub.data.rp.repository.RpRepository
import me.rerere.rikkahub.utils.JsonInstant
import kotlin.uuid.Uuid

class RpRuntime(
    private val settingsStore: SettingsStore,
    private val rpRepository: RpRepository,
    private val conversationRepository: ConversationRepository,
    private val modelGateway: RpModelGateway,
    private val stateReducer: RpStateReducer,
) {
    private val sessionLocks = mutableMapOf<Uuid, Mutex>()

    private fun lockFor(sessionId: Uuid): Mutex = synchronized(sessionLocks) {
        sessionLocks.getOrPut(sessionId) { Mutex() }
    }

    suspend fun submitInput(sessionId: Uuid, input: String): RpTurn = lockFor(sessionId).withLock {
        val session = rpRepository.getSession(sessionId) ?: error("RP session not found")
        require(input.isNotBlank()) { "RP input must not be blank" }
        val turn = RpTurn(
            sessionId = sessionId,
            branchId = session.activeBranchId,
            input = input.trim(),
            status = RpTurnStatus.ADJUDICATING,
        )
        rpRepository.createTurn(turn)
        runTurn(session, turn)
    }

    suspend fun retryPhase(turnId: Uuid, phase: me.rerere.rikkahub.data.rp.model.RpTurnPhase): RpTurn =
        lockFor(rpRepository.getTurn(turnId)?.sessionId ?: error("RP turn not found")).withLock {
            val turn = rpRepository.getTurn(turnId) ?: error("RP turn not found")
            val session = rpRepository.getSession(turn.sessionId) ?: error("RP session not found")
            when (phase) {
                me.rerere.rikkahub.data.rp.model.RpTurnPhase.NARRATION -> {
                    val outcome = turn.outcome ?: error("Turn has no accepted outcome")
                    val narrative = narrate(settingsStore.settingsFlow.value, session, turn, outcome)
                    turn.copy(status = RpTurnStatus.COMPLETED, narrative = narrative).also { rpRepository.updateTurn(it) }
                }
                me.rerere.rikkahub.data.rp.model.RpTurnPhase.ADJUDICATION,
                me.rerere.rikkahub.data.rp.model.RpTurnPhase.REVIEW -> {
                    val reset = turn.copy(status = RpTurnStatus.ADJUDICATING, error = null)
                    rpRepository.updateTurn(reset)
                    runTurn(session, reset)
                }
            }
        }

    suspend fun forkFromTurn(turnId: Uuid): RpSession {
        val turn = rpRepository.getTurn(turnId) ?: error("RP turn not found")
        val session = rpRepository.getSession(turn.sessionId) ?: error("RP session not found")
        val newBranchId = Uuid.random()
        rpRepository.getTurns(session.id, session.activeBranchId)
            .takeWhile { it.id != turn.id }
            .plus(turn)
            .forEach { source ->
                rpRepository.createTurn(
                    source.copy(
                        id = Uuid.random(),
                        branchId = newBranchId,
                    )
                )
            }
        return session.copy(
            activeBranchId = newBranchId,
            state = turn.stateAfter ?: session.card.initialState,
            revision = session.revision + 1,
            updatedAt = System.currentTimeMillis(),
        ).also { rpRepository.updateSession(it) }
    }

    suspend fun rollbackToTurn(turnId: Uuid): RpSession = forkFromTurn(turnId)

    private suspend fun runTurn(session: RpSession, initialTurn: RpTurn): RpTurn {
        var turn = initialTurn
        return try {
            val settings = settingsStore.settingsFlow.value
            val outcome = if (session.card.hasAdjudication()) {
                adjudicate(settings, session, turn)
            } else {
                RpOutcome(summary = "", facts = emptyList(), state = session.state)
            }
            turn = turn.copy(status = RpTurnStatus.REVIEWING, outcome = outcome)
            rpRepository.updateTurn(turn)

            val review = review(settings, session, turn, outcome)
            turn = turn.copy(review = review)
            if (!review.accepted) {
                return turn.copy(
                    status = RpTurnStatus.FAILED,
                    error = review.reason.ifBlank { "RP result was rejected" },
                ).also { rpRepository.updateTurn(it) }
            }

            val nextState = stateReducer.apply(session.state, outcome.changes)
            val committedSession = session.copy(
                revision = session.revision + 1,
                state = nextState,
                updatedAt = System.currentTimeMillis(),
            )
            rpRepository.updateSession(committedSession)

            turn = turn.copy(status = RpTurnStatus.NARRATING, stateAfter = nextState)
            rpRepository.updateTurn(turn)
            val narrative = narrate(settings, committedSession, turn, outcome)
            turn = turn.copy(status = RpTurnStatus.COMPLETED, narrative = narrative)
            rpRepository.updateTurn(turn)
            turn
        } catch (error: Exception) {
            turn.copy(status = RpTurnStatus.FAILED, error = error.message ?: error.javaClass.simpleName)
                .also { rpRepository.updateTurn(it) }
        }
    }

    private suspend fun adjudicate(
        settings: me.rerere.rikkahub.data.datastore.Settings,
        session: RpSession,
        turn: RpTurn,
    ): RpOutcome {
        val model = resolveModel(settings, session.card.modelBindings.adjudicatorModelId)
            ?: error("No RP adjudicator model selected")
        val output = modelGateway.complete(
            settings = settings,
            model = model,
            sessionId = session.id,
            systemPrompt = buildAdjudicatorSystemPrompt(session.card),
            userPrompt = buildAdjudicatorInput(session, turn.input),
        )
        return parseOutcome(output, session.state)
    }

    private suspend fun review(
        settings: me.rerere.rikkahub.data.datastore.Settings,
        session: RpSession,
        turn: RpTurn,
        outcome: RpOutcome,
    ): RpReview {
        val model = resolveModel(settings, session.card.modelBindings.reviewerModelId)
            ?: return RpReview(accepted = true, reason = "Reviewer disabled")
        val output = modelGateway.complete(
            settings = settings,
            model = model,
            sessionId = session.id,
            systemPrompt = """
                You review a proposed RP outcome. Return JSON only:
                {"accepted":true,"confidence":0.0,"reason":"","retryable":false}
                Reject outcomes that invent unsupported facts, confuse a player's attempt with success,
                contradict the supplied state, or reveal hidden information.
            """.trimIndent(),
            userPrompt = buildString {
                appendLine("Player input:")
                appendLine(turn.input)
                appendLine("Current state:")
                appendLine(JsonInstant.encodeToString(session.state))
                appendLine("Proposed outcome:")
                appendLine(JsonInstant.encodeToString(outcome))
            },
        )
        val json = modelGateway.parseJsonObject(output)
        return RpReview(
            accepted = json["accepted"]?.jsonPrimitive?.booleanOrNull ?: false,
            confidence = json["confidence"]?.jsonPrimitive?.floatOrNull,
            reason = json["reason"]?.jsonPrimitive?.content.orEmpty(),
            retryable = json["retryable"]?.jsonPrimitive?.booleanOrNull ?: false,
        )
    }

    private suspend fun narrate(
        settings: me.rerere.rikkahub.data.datastore.Settings,
        session: RpSession,
        turn: RpTurn,
        outcome: RpOutcome,
    ): String {
        val model = resolveModel(settings, session.card.modelBindings.narratorModelId)
            ?: error("No RP narrator model selected")
        return modelGateway.complete(
            settings = settings,
            model = model,
            sessionId = session.id,
            systemPrompt = buildNarratorSystemPrompt(session.card),
            userPrompt = buildString {
                appendLine("Player input:")
                appendLine(turn.input)
                appendLine("Accepted outcome:")
                appendLine(JsonInstant.encodeToString(outcome))
                appendLine("Current visible state:")
                appendLine(JsonInstant.encodeToString(stateReducer.visibleState(session.state, session.card.hiddenStatePaths)))
            },
        )
    }

    private fun parseOutcome(text: String, currentState: JsonObject): RpOutcome {
        val json = modelGateway.parseJsonObject(text)
        val changes = json["changes"]?.let { JsonInstant.decodeFromJsonElement<List<RpStateChange>>(it) }.orEmpty()
        return RpOutcome(
            summary = json["summary"]?.jsonPrimitive?.content.orEmpty(),
            facts = json["facts"]?.let { JsonInstant.decodeFromJsonElement<List<String>>(it) }.orEmpty(),
            nextPrompt = json["nextPrompt"]?.jsonPrimitive?.content.orEmpty(),
            changes = changes,
            state = currentState,
            needsChoice = json["needsChoice"]?.jsonPrimitive?.booleanOrNull ?: false,
        )
    }

    private fun resolveModel(settings: me.rerere.rikkahub.data.datastore.Settings, id: Uuid?): Model? =
        settings.findModelById(id ?: settings.chatModelId)

    private fun buildAdjudicatorSystemPrompt(card: RpCard): String = buildString {
        appendLine("You are the world adjudicator for an interactive roleplay.")
        appendLine("Decide consequences; do not write prose. A player's attempt is not automatically a success.")
        appendLine("Return JSON only with summary, facts, changes, nextPrompt, needsChoice.")
        appendLine("changes is an array of {path, operation, value}; operation is SET, APPEND, or REMOVE.")
        appendLine("World setting:")
        appendLine(card.worldPrompt)
        appendLine("Rules:")
        appendLine(card.rulesPrompt)
    }

    private fun buildAdjudicatorInput(session: RpSession, input: String): String = buildString {
        appendLine("Current confirmed state:")
        appendLine(JsonInstant.encodeToString(session.state))
        appendLine("Player action:")
        appendLine(input)
    }

    private fun buildNarratorSystemPrompt(card: RpCard): String = buildString {
        appendLine("You are the narrator for an interactive roleplay.")
        appendLine("Describe only the accepted outcome. Do not decide new facts, actions, or dialogue for the player.")
        if (card.narrativeStyle.isNotBlank()) {
            appendLine("Narrative style:")
            appendLine(card.narrativeStyle)
        }
        appendLine("World setting:")
        appendLine(card.worldPrompt)
    }
}

private val kotlinx.serialization.json.JsonPrimitive.booleanOrNull: Boolean?
    get() = content.toBooleanStrictOrNull()

private val kotlinx.serialization.json.JsonPrimitive.floatOrNull: Float?
    get() = content.toFloatOrNull()
