package me.rerere.rikkahub.data.rp.runtime

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.rerere.rikkahub.data.rp.model.RpCard
import me.rerere.rikkahub.data.rp.model.RpCommittedEvent
import me.rerere.rikkahub.data.rp.model.RpModelRole
import me.rerere.rikkahub.data.rp.model.RpSession
import me.rerere.rikkahub.data.rp.model.RpStateField
import me.rerere.rikkahub.data.rp.model.RpStateFieldType
import me.rerere.rikkahub.data.rp.model.RpStateSchema
import me.rerere.rikkahub.data.rp.model.RpStoryMemory
import me.rerere.rikkahub.data.rp.model.RpTurn
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.uuid.Uuid

class RpContextBuilderTest {
    @Test
    fun narratorReceivesOnlyVisibleStateAndCommittedStoryContext() {
        val card = RpCard(
            worldPrompt = "暴雪覆盖了北方城市。",
            rulesPrompt = "行动不自动成功。",
            hiddenStatePaths = setOf("world.raiderPlan"),
            stateSchema = RpStateSchema(
                fields = listOf(
                    RpStateField("time.day", "生存天数", RpStateFieldType.NUMBER),
                    RpStateField("player.health", "生命", RpStateFieldType.PROGRESS, min = 0.0, max = 100.0),
                ),
            ),
        )
        val state = buildJsonObject {
            put("time", buildJsonObject { put("day", 12) })
            put("player", buildJsonObject { put("health", 78) })
            put("world", buildJsonObject { put("raiderPlan", "午夜袭击" ) })
        }
        val session = RpSession(
            card = card,
            conversationId = Uuid.random(),
            state = state,
            storyMemory = RpStoryMemory(summary = "林夏左臂受伤，队伍正在找无线电。"),
        )
        val event = RpCommittedEvent(
            turnId = Uuid.random(),
            stateVersion = 1,
            summary = "在加油站找到两瓶水。",
            facts = listOf("搜索持续两小时"),
        )
        val turn = RpTurn(
            sessionId = session.id,
            branchId = session.activeBranchId,
            input = "我搜索货架",
            event = event,
        )

        val request = RpContextBuilder(RpStateReducer()).buildNarration(
            session = session,
            priorTurns = listOf(turn),
            event = event,
        )

        assertTrue(request.systemPrompt.contains("暴雪覆盖了北方城市"))
        assertTrue(request.userPrompt.contains("在加油站找到两瓶水"))
        assertTrue(request.userPrompt.contains("林夏左臂受伤"))
        assertTrue(request.userPrompt.contains("\"day\":12"))
        assertFalse(request.userPrompt.contains("午夜袭击"))
        assertFalse(request.userPrompt.contains("我搜索货架"))
    }

    @Test
    fun adjudicatorReceivesActionAndAuthoritativeState() {
        val card = RpCard(worldPrompt = "资源稀缺。", rulesPrompt = "不允许凭空获得物资。")
        val session = RpSession(
            card = card,
            conversationId = Uuid.random(),
            state = buildJsonObject { put("inventory", buildJsonObject { put("water", 1) }) },
        )

        val request = RpContextBuilder(RpStateReducer()).buildAdjudication(
            session = session,
            priorTurns = emptyList(),
            action = "我喝一瓶水",
        )

        assertTrue(request.role == RpModelRole.ADJUDICATOR)
        assertTrue(request.userPrompt.contains("我喝一瓶水"))
        assertTrue(request.userPrompt.contains("\"water\":1"))
    }
}
