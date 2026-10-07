package me.rerere.rikkahub.data.rp.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.jsonObject
import me.rerere.rikkahub.data.db.dao.RpSessionDAO
import me.rerere.rikkahub.data.db.dao.RpTurnDAO
import me.rerere.rikkahub.data.db.dao.RpCardDAO
import me.rerere.rikkahub.data.db.entity.RpCardEntity
import me.rerere.rikkahub.data.db.entity.RpSessionEntity
import me.rerere.rikkahub.data.db.entity.RpTurnEntity
import me.rerere.rikkahub.data.rp.model.RpCard
import me.rerere.rikkahub.data.rp.model.RpReview
import me.rerere.rikkahub.data.rp.model.RpSession
import me.rerere.rikkahub.data.rp.model.RpSessionSnapshot
import me.rerere.rikkahub.data.rp.model.RpStateChange
import me.rerere.rikkahub.data.rp.model.RpTurn
import me.rerere.rikkahub.data.rp.model.RpTurnStatus
import me.rerere.rikkahub.data.rp.model.RpOutcome
import me.rerere.rikkahub.utils.JsonInstant
import kotlin.uuid.Uuid

class RpRepository(
    private val sessionDao: RpSessionDAO,
    private val turnDao: RpTurnDAO,
    private val cardDao: RpCardDAO,
) {
    fun observeCards(): Flow<List<RpCard>> = cardDao.observeAll().map { cards ->
        cards.map { JsonInstant.decodeFromString<RpCard>(it.cardJson) }
    }

    suspend fun getCard(cardId: Uuid): RpCard? = cardDao.getById(cardId.toString())
        ?.let { JsonInstant.decodeFromString(it.cardJson) }

    suspend fun saveCard(card: RpCard) {
        cardDao.insert(
            RpCardEntity(
                id = card.id.toString(),
                version = card.version,
                cardJson = JsonInstant.encodeToString(card),
                createdAt = card.createdAt,
                updatedAt = card.updatedAt,
            )
        )
    }

    suspend fun deleteCard(card: RpCard) {
        cardDao.delete(
            RpCardEntity(card.id.toString(), card.version, JsonInstant.encodeToString(card), card.createdAt, card.updatedAt)
        )
    }

    fun observeSessions(): Flow<List<RpSession>> = sessionDao.observeAll().mapToSessions()

    fun observeSnapshot(sessionId: Uuid): Flow<RpSessionSnapshot?> =
        sessionDao.observeById(sessionId.toString()).flatMapTurns(turnDao)

    suspend fun getSession(sessionId: Uuid): RpSession? = sessionDao.getById(sessionId.toString())?.toModel()

    suspend fun getSessionByConversation(conversationId: Uuid): RpSession? =
        sessionDao.getByConversationId(conversationId.toString())?.toModel()

    suspend fun deleteSession(sessionId: Uuid) {
        turnDao.deleteForSession(sessionId.toString())
        sessionDao.getById(sessionId.toString())?.let { sessionDao.delete(it) }
    }

    suspend fun deleteSessionByConversation(conversationId: Uuid) {
        getSessionByConversation(conversationId)?.let { deleteSession(it.id) }
    }

    suspend fun createSession(session: RpSession) {
        sessionDao.insert(session.toEntity())
    }

    suspend fun updateSession(session: RpSession) {
        sessionDao.update(session.toEntity())
    }

    suspend fun createTurn(turn: RpTurn) {
        turnDao.insert(turn.toEntity())
    }

    suspend fun updateTurn(turn: RpTurn) {
        turnDao.update(turn.toEntity())
    }

    suspend fun getTurn(turnId: Uuid): RpTurn? = turnDao.getById(turnId.toString())?.toModel()

    suspend fun getTurns(sessionId: Uuid, branchId: Uuid): List<RpTurn> =
        turnDao.getForBranch(sessionId.toString(), branchId.toString()).map { it.toModel() }
}

private fun Flow<List<RpSessionEntity>>.mapToSessions(): Flow<List<RpSession>> =
    this.map { entities -> entities.map { it.toModel() } }

private fun Flow<RpSessionEntity?>.flatMapTurns(
    turnDao: RpTurnDAO,
): Flow<RpSessionSnapshot?> = this.flatMapLatest { entity ->
    if (entity == null) {
        flowOf(null)
    } else {
        turnDao.observeForBranch(entity.id, entity.activeBranchId)
            .map { turns -> RpSessionSnapshot(entity.toModel(), turns.map { it.toModel() }) }
    }
}

private fun RpSessionEntity.toModel(): RpSession = RpSession(
    id = Uuid.parse(id),
    card = JsonInstant.decodeFromString(cardJson),
    conversationId = Uuid.parse(conversationId),
    activeBranchId = Uuid.parse(activeBranchId),
    revision = revision,
    state = JsonInstant.parseToJsonElement(stateJson).jsonObject,
    status = me.rerere.rikkahub.data.rp.model.RpSessionStatus.valueOf(status),
    createdAt = createdAt,
    updatedAt = updatedAt,
)

private fun RpSession.toEntity(): RpSessionEntity = RpSessionEntity(
    id = id.toString(),
    cardId = card.id.toString(),
    conversationId = conversationId.toString(),
    cardJson = JsonInstant.encodeToString(card),
    title = card.name,
    activeBranchId = activeBranchId.toString(),
    revision = revision,
    stateJson = JsonInstant.encodeToString(state),
    status = status.name,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

private fun RpTurnEntity.toModel(): RpTurn = RpTurn(
    id = Uuid.parse(id),
    sessionId = Uuid.parse(sessionId),
    branchId = Uuid.parse(branchId),
    input = input,
    status = RpTurnStatus.valueOf(status),
    outcome = outcomeJson.takeIf { it.isNotBlank() }?.let { JsonInstant.decodeFromString<RpOutcome>(it) },
    review = reviewJson.takeIf { it.isNotBlank() }?.let { JsonInstant.decodeFromString<RpReview>(it) },
    narrative = narrative,
    stateAfter = stateAfterJson.takeIf { it.isNotBlank() }?.let { JsonInstant.parseToJsonElement(it).jsonObject },
    error = error,
    createdAt = createdAt,
)

private fun RpTurn.toEntity(): RpTurnEntity = RpTurnEntity(
    id = id.toString(),
    sessionId = sessionId.toString(),
    branchId = branchId.toString(),
    input = input,
    status = status.name,
    outcomeJson = outcome?.let(JsonInstant::encodeToString).orEmpty(),
    reviewJson = review?.let(JsonInstant::encodeToString).orEmpty(),
    narrative = narrative,
    stateAfterJson = stateAfter?.let(JsonInstant::encodeToString).orEmpty(),
    error = error,
    createdAt = createdAt,
)
