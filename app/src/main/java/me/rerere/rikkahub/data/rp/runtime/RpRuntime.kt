package me.rerere.rikkahub.data.rp.runtime

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.ai.provider.Model
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.findModelById
import me.rerere.rikkahub.data.rp.model.RpCommittedEvent
import me.rerere.rikkahub.data.rp.model.RpOutcome
import me.rerere.rikkahub.data.rp.model.RpReview
import me.rerere.rikkahub.data.rp.model.RpSession
import me.rerere.rikkahub.data.rp.model.RpStateChange
import me.rerere.rikkahub.data.rp.model.RpTurn
import me.rerere.rikkahub.data.rp.model.RpTurnPhase
import me.rerere.rikkahub.data.rp.model.RpTurnStatus
import me.rerere.rikkahub.data.rp.model.hasAdjudication
import me.rerere.rikkahub.data.rp.repository.RpRepository
import me.rerere.rikkahub.utils.JsonInstant
import kotlin.uuid.Uuid

class RpRuntime(
    private val settingsStore: SettingsStore,
    private val rpRepository: RpRepository,
    private val modelGateway: RpModelGateway,
    private val stateReducer: RpStateReducer,
    private val contextBuilder: RpContextBuilder,
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
            stateBefore = session.state,
        )
        rpRepository.createTurn(turn)
        runTurn(session, turn)
    }

    suspend fun retryPhase(turnId: Uuid, phase: RpTurnPhase): RpTurn =
        lockFor(rpRepository.getTurn(turnId)?.sessionId ?: error("RP turn not found")).withLock {
            val turn = rpRepository.getTurn(turnId) ?: error("RP turn not found")
            val session = rpRepository.getSession(turn.sessionId) ?: error("RP session not found")
            when (phase) {
                RpTurnPhase.NARRATION -> {
                    val event = turn.event ?: error("Turn has no committed event")
                    val narrative = narrate(settingsStore.settingsFlow.value, session, priorTurns(session), event)
                    turn.copy(status = RpTurnStatus.COMPLETED, narrative = narrative, error = null)
                        .also { rpRepository.updateTurn(it) }
                }
                RpTurnPhase.ADJUDICATION,
                RpTurnPhase.REVIEW -> {
                    val reset = turn.copy(
                        status = RpTurnStatus.ADJUDICATING,
                        outcome = null,
                        review = null,
                        stateAfter = null,
                        event = null,
                        error = null,
                    )
                    rpRepository.updateTurn(reset)
                    runTurn(session.copy(state = turn.stateBefore ?: session.state), reset)
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
                rpRepository.createTurn(source.copy(id = Uuid.random(), branchId = newBranchId))
            }
        val branchTurns = rpRepository.getTurns(session.id, newBranchId)
        return session.copy(
            activeBranchId = newBranchId,
            state = turn.stateAfter ?: turn.stateBefore ?: session.card.initialState,
            revision = turn.event?.stateVersion ?: session.revision,
            storyMemory = rebuildStoryMemory(branchTurns),
            updatedAt = System.currentTimeMillis(),
        ).also { rpRepository.updateSession(it) }
    }

    suspend fun rollbackToTurn(turnId: Uuid): RpSession = forkFromTurn(turnId)

    private suspend fun runTurn(session: RpSession, initialTurn: RpTurn): RpTurn {
        var turn = initialTurn
        return try {
            val settings = settingsStore.settingsFlow.value
            val history = priorTurns(session)
            val adjudicationEnabled = session.card.hasAdjudication()
            val outcome = if (adjudicationEnabled) {
                adjudicate(settings, session, history, turn.input)
            } else {
                RpOutcome(
                    summary = turn.input,
                    facts = listOf(turn.input),
                    state = session.state,
                )
            }
            turn = turn.copy(status = RpTurnStatus.REVIEWING, outcome = outcome)
            rpRepository.updateTurn(turn)

            val review = if (adjudicationEnabled) {
                review(settings, session, history, turn.input, outcome)
            } else {
                RpReview(accepted = true)
            }
            turn = turn.copy(review = review)
            if (!review.accepted) {
                return turn.copy(
                    status = RpTurnStatus.FAILED,
                    error = review.reason.ifBlank { "世界裁决未通过规则审查" },
                ).also { rpRepository.updateTurn(it) }
            }

            val committedOutcome = if (adjudicationEnabled) {
                maintainState(settings, session, history, turn.input, outcome)
            } else {
                outcome
            }
            turn = turn.copy(outcome = committedOutcome)
            val nextState = stateReducer.apply(session.state, committedOutcome.changes, session.card.stateSchema)
            val event = RpCommittedEvent(
                turnId = turn.id,
                stateVersion = session.revision + 1,
                summary = committedOutcome.summary,
                facts = committedOutcome.facts,
                changes = committedOutcome.changes,
                publicState = stateReducer.visibleState(
                    state = nextState,
                    hiddenPaths = session.card.hiddenStatePaths,
                    schema = session.card.stateSchema,
                ),
            )
            val committedSession = session.copy(
                revision = event.stateVersion,
                state = nextState,
                storyMemory = session.storyMemory.record(event),
                updatedAt = System.currentTimeMillis(),
            )
            rpRepository.updateSession(committedSession)

            turn = turn.copy(
                status = RpTurnStatus.NARRATING,
                stateBefore = session.state,
                stateAfter = nextState,
                event = event,
            )
            rpRepository.updateTurn(turn)
            val narrative = narrate(settings, committedSession, history, event)
            turn.copy(status = RpTurnStatus.COMPLETED, narrative = narrative, error = null)
                .also { rpRepository.updateTurn(it) }
        } catch (error: Exception) {
            turn.copy(status = RpTurnStatus.FAILED, error = error.message ?: error.javaClass.simpleName)
                .also { rpRepository.updateTurn(it) }
        }
    }

    private suspend fun adjudicate(
        settings: Settings,
        session: RpSession,
        history: List<RpTurn>,
        action: String,
    ): RpOutcome {
        val model = resolveModel(settings, session.card.modelBindings.adjudicatorModelId)
            ?: error("未找到世界裁判模型")
        val request = contextBuilder.buildAdjudication(session, history, action)
        return parseOutcome(
            modelGateway.complete(settings, model, request.systemPrompt, request.userPrompt, session.id),
            session.state,
        )
    }

    private suspend fun maintainState(
        settings: Settings,
        session: RpSession,
        history: List<RpTurn>,
        action: String,
        outcome: RpOutcome,
    ): RpOutcome {
        val modelId = session.card.modelBindings.stateKeeperModelId ?: return outcome
        val model = resolveModel(settings, modelId) ?: error("未找到状态记录模型")
        val request = contextBuilder.buildStateKeeping(session, history, action, outcome)
        return parseOutcome(
            modelGateway.complete(settings, model, request.systemPrompt, request.userPrompt, session.id),
            session.state,
            fallback = outcome,
        )
    }

    private suspend fun review(
        settings: Settings,
        session: RpSession,
        history: List<RpTurn>,
        action: String,
        outcome: RpOutcome,
    ): RpReview {
        val modelId = session.card.modelBindings.reviewerModelId ?: return RpReview(accepted = true)
        val model = resolveModel(settings, modelId) ?: error("未找到规则审查模型")
        val request = contextBuilder.buildReview(session, history, action, outcome)
        val json = modelGateway.parseJsonObject(
            modelGateway.complete(settings, model, request.systemPrompt, request.userPrompt, session.id),
        )
        return RpReview(
            accepted = json["accepted"]?.jsonPrimitive?.booleanOrNull ?: false,
            confidence = json["confidence"]?.jsonPrimitive?.floatOrNull,
            reason = json["reason"]?.jsonPrimitive?.content.orEmpty(),
            retryable = json["retryable"]?.jsonPrimitive?.booleanOrNull ?: false,
        )
    }

    private suspend fun narrate(
        settings: Settings,
        session: RpSession,
        history: List<RpTurn>,
        event: RpCommittedEvent,
    ): String {
        val model = resolveModel(settings, session.card.modelBindings.narratorModelId)
            ?: error("未找到叙事模型")
        val request = contextBuilder.buildNarration(session, history, event)
        return modelGateway.complete(settings, model, request.systemPrompt, request.userPrompt, session.id)
    }

    private fun parseOutcome(text: String, currentState: JsonObject, fallback: RpOutcome? = null): RpOutcome {
        val json = modelGateway.parseJsonObject(text)
        val changes = json["changes"]?.let { JsonInstant.decodeFromJsonElement<List<RpStateChange>>(it) }
            ?: fallback?.changes.orEmpty()
        return RpOutcome(
            summary = json["summary"]?.jsonPrimitive?.content ?: fallback?.summary.orEmpty(),
            facts = json["facts"]?.let { JsonInstant.decodeFromJsonElement<List<String>>(it) } ?: fallback?.facts.orEmpty(),
            nextPrompt = json["nextPrompt"]?.jsonPrimitive?.content ?: fallback?.nextPrompt.orEmpty(),
            changes = changes,
            state = currentState,
            needsChoice = json["needsChoice"]?.jsonPrimitive?.booleanOrNull ?: fallback?.needsChoice ?: false,
        )
    }

    private suspend fun priorTurns(session: RpSession): List<RpTurn> =
        rpRepository.getTurns(session.id, session.activeBranchId)

    private fun rebuildStoryMemory(turns: List<RpTurn>) =
        turns.mapNotNull { it.event }.fold(me.rerere.rikkahub.data.rp.model.RpStoryMemory()) { memory, event ->
            memory.record(event)
        }

    private fun resolveModel(settings: Settings, id: Uuid?): Model? =
        settings.findModelById(id ?: settings.chatModelId)
}

private val kotlinx.serialization.json.JsonPrimitive.booleanOrNull: Boolean?
    get() = content.toBooleanStrictOrNull()

private val kotlinx.serialization.json.JsonPrimitive.floatOrNull: Float?
    get() = content.toFloatOrNull()
