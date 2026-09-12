package me.rerere.rikkahub.data.ai.groupchat

import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.GroupChatSeat
import me.rerere.rikkahub.data.model.GroupChatTemplate
import me.rerere.rikkahub.data.model.applyGroupSeat
import me.rerere.rikkahub.data.model.buildSeatDisplayNames
import me.rerere.rikkahub.data.model.resolveGroupChatDisplaySeat
import me.rerere.rikkahub.data.model.resolveGroupChatModelId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.uuid.Uuid

class GroupChatEngineTest {
    private val gpt = Assistant(id = Uuid.parse("11111111-1111-1111-1111-111111111111"), name = "GPT")
    private val claude = Assistant(id = Uuid.parse("22222222-2222-2222-2222-222222222222"), name = "Claude")
    private val gptSeat = GroupChatSeat(id = Uuid.parse("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"), assistantId = gpt.id)
    private val claudeSeat = GroupChatSeat(id = Uuid.parse("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"), assistantId = claude.id)
    private val template = GroupChatTemplate(
        id = Uuid.parse("33333333-3333-3333-3333-333333333333"),
        name = "Code pair",
        seats = listOf(gptSeat, claudeSeat),
    )
    private val assistants = mapOf(gpt.id to gpt, claude.id to claude)

    @Test
    fun mentionPicksThatSeatAndBecomesSticky() {
        val speakers = GroupChatEngine.resolveSpeakerSeatIds(
            userText = "@GPT write the feature",
            template = template,
            assistantsById = assistants,
            stickySeatId = claudeSeat.id,
        )
        assertEquals(listOf(gptSeat.id), speakers)
        assertEquals(gptSeat.id, GroupChatEngine.nextStickySeatId(speakers, claudeSeat.id))
    }

    @Test
    fun noMentionKeepsLastAtSeat() {
        val speakers = GroupChatEngine.resolveSpeakerSeatIds(
            userText = "continue with the tests",
            template = template,
            assistantsById = assistants,
            stickySeatId = gptSeat.id,
        )
        assertEquals(listOf(gptSeat.id), speakers)
    }

    @Test
    fun disabledSeatIsSkippedWhenNoMention() {
        val disabledFirst = gptSeat.copy(defaultEnabled = false)
        val speakers = GroupChatEngine.resolveSpeakerSeatIds(
            userText = "hello everyone",
            template = template.copy(seats = listOf(disabledFirst, claudeSeat)),
            assistantsById = assistants,
            stickySeatId = null,
        )
        assertEquals(listOf(claudeSeat.id), speakers)
    }

    @Test
    fun introPrefixedOntoSeatSystemPrompt() {
        val names = template.buildSeatDisplayNames(assistants)
        val suffix = GroupChatEngine.contextSystemPromptSuffix(
            template = template.copy(intro = "Shared code review room."),
            seat = claudeSeat,
            seatDisplayNames = names,
        )
        assertTrue(suffix.startsWith("Shared code review room."))
        assertTrue(suffix.contains("You are Claude in a group chat."))
    }

    @Test
    fun neverMentionedFallsToFirstSeatNotHostRouter() {
        val speakers = GroupChatEngine.resolveSpeakerSeatIds(
            userText = "hello everyone",
            template = template,
            assistantsById = assistants,
            stickySeatId = null,
        )
        assertEquals(listOf(gptSeat.id), speakers)
    }

    @Test
    fun mentionsFollowAppearanceOrderAndDedupe() {
        val speakers = GroupChatEngine.resolveSpeakerSeatIds(
            userText = "@Claude then @GPT then @Claude again",
            template = template,
            assistantsById = assistants,
            stickySeatId = null,
        )
        assertEquals(listOf(claudeSeat.id, gptSeat.id), speakers)
    }

    @Test
    fun multiMentionClearsSticky() {
        val sticky = GroupChatEngine.nextStickySeatId(
            speakerSeatIds = listOf(gptSeat.id, claudeSeat.id),
            previousSticky = gptSeat.id,
            clearAfterMultiMention = true,
        )
        assertEquals(null, sticky)
    }

    @Test
    fun singleMentionKeepsStickyOnThatSeat() {
        val sticky = GroupChatEngine.nextStickySeatId(
            speakerSeatIds = listOf(claudeSeat.id),
            previousSticky = gptSeat.id,
            clearAfterMultiMention = false,
        )
        assertEquals(claudeSeat.id, sticky)
    }

    @Test
    fun disabledMentionIsIgnoredAndDoesNotCountAsMultiMention() {
        val disabledGpt = gptSeat.copy(defaultEnabled = false)
        val group = template.copy(seats = listOf(disabledGpt, claudeSeat))
        val mentioned = GroupChatEngine.resolveEnabledMentionedSeatIds(
            text = "@GPT @Claude review",
            template = group,
            assistantsById = assistants,
        )
        assertEquals(listOf(claudeSeat.id), mentioned)
        val speakers = GroupChatEngine.resolveSpeakerSeatIds(
            userText = "@GPT @Claude review",
            template = group,
            assistantsById = assistants,
            stickySeatId = null,
        )
        assertEquals(listOf(claudeSeat.id), speakers)
        assertEquals(
            claudeSeat.id,
            GroupChatEngine.nextStickySeatId(
                speakerSeatIds = speakers,
                previousSticky = null,
                clearAfterMultiMention = mentioned.size >= 2,
            ),
        )
    }

    @Test
    fun newSeatTurnReusesSameSeatAssistantBubble() {
        val claudeReply = UIMessage.assistant("draft").copy(speakerSeatId = claudeSeat.id)
        val reused = GroupChatEngine.messagesForNewSeatTurn(
            messages = listOf(UIMessage.user("@Claude again"), claudeReply),
            seatId = claudeSeat.id,
        )
        assertEquals(2, reused.size)
        assertEquals(claudeReply.id, reused.last().id)
    }

    @Test
    fun newSeatTurnOpensAFreshAssistantMessage() {
        val gptReply = UIMessage.assistant("done").copy(speakerSeatId = gptSeat.id)
        val opened = GroupChatEngine.messagesForNewSeatTurn(
            messages = listOf(UIMessage.user("@Claude review"), gptReply),
            seatId = claudeSeat.id,
        )
        assertEquals(3, opened.size)
        assertEquals(MessageRole.ASSISTANT, opened.last().role)
        assertEquals(claudeSeat.id, opened.last().speakerSeatId)
        assertTrue(opened.last().parts.isEmpty())
    }

    @Test
    fun otherSeatToolOutputIsLabelledUserNote() {
        val names = template.buildSeatDisplayNames(assistants)
        val gptReply = UIMessage(
            role = MessageRole.ASSISTANT,
            parts = listOf(
                UIMessagePart.Text("I wrote the file"),
                UIMessagePart.Tool(
                    toolCallId = "1",
                    toolName = "workspace_write_file",
                    input = """{"path":"src/App.kt"}""",
                    output = listOf(UIMessagePart.Text("diff --git a/src/App.kt\n+fun main() {}")),
                ),
            ),
            speakerSeatId = gptSeat.id,
            speakerAssistantId = gpt.id,
        )
        val user = UIMessage.user("@Claude review this")
        val rewritten = GroupChatEngine.rewritePromptMessagesForSeat(
            messages = listOf(user, gptReply),
            seat = claudeSeat,
            selfAssistantId = claude.id,
            seatDisplayNames = names,
            assistantsById = assistants,
            userName = "Roc",
        )
        assertEquals(2, rewritten.size)
        assertEquals(MessageRole.USER, rewritten[1].role)
        val text = rewritten[1].toText()
        assertTrue(text.contains("[Message from GPT (assistant)]"))
        assertTrue(text.contains("[Tool output from GPT]"))
        assertTrue(text.contains("src/App.kt") || text.contains("fun main"))
        assertTrue(rewritten.none { it.role == MessageRole.ASSISTANT && it.speakerSeatId == gptSeat.id })
    }

    @Test
    fun applyGroupSeatKeepsIdentityAndDropsAssistantBindings() {
        val lorebookId = Uuid.parse("44444444-4444-4444-4444-444444444444")
        val modeId = Uuid.parse("55555555-5555-5555-5555-555555555555")
        val mcpId = Uuid.parse("66666666-6666-6666-6666-666666666666")
        val seated = gpt.copy(
            systemPrompt = "Original",
            enableMemory = true,
            enableSessionMemory = true,
            enableWebSearch = true,
            useGlobalMemory = true,
            enableRecentChatsReference = true,
            enableTimeReminder = true,
            mcpServers = setOf(mcpId),
            enabledSkills = setOf("solo-skill"),
            lorebookIds = setOf(lorebookId),
            modeInjectionIds = setOf(modeId),
            allowConversationPromptInjection = true,
            allowConversationSystemPrompt = true,
        ).applyGroupSeat(template, gptSeat)
        assertEquals("Original", seated.systemPrompt)
        assertEquals(false, seated.enableMemory)
        assertEquals(false, seated.enableSessionMemory)
        assertEquals(false, seated.enableWebSearch)
        assertEquals(false, seated.useGlobalMemory)
        assertEquals(false, seated.enableRecentChatsReference)
        assertEquals(false, seated.enableTimeReminder)
        assertEquals(emptySet<Uuid>(), seated.mcpServers)
        assertEquals(emptySet<String>(), seated.enabledSkills)
        assertEquals(emptySet<Uuid>(), seated.lorebookIds)
        assertEquals(emptySet<Uuid>(), seated.modeInjectionIds)
        assertEquals(false, seated.allowConversationPromptInjection)
        assertEquals(false, seated.allowConversationSystemPrompt)
    }

    @Test
    fun applyGroupSeatUsesSeatSkillsAndOverridePrompt() {
        val lorebookId = Uuid.parse("44444444-4444-4444-4444-444444444444")
        val overrideSeat = gptSeat.copy(
            overrides = gptSeat.overrides.copy(
                systemPrompt = "Reviewer only.",
                enabledSkills = setOf("workspace-python"),
                lorebookIds = setOf(lorebookId),
                enableMemory = true,
                enableWebSearch = true,
            ),
        )
        val seated = gpt.copy(
            enabledSkills = setOf("solo-skill"),
            systemPrompt = "Original",
            enableMemory = false,
            enableWebSearch = false,
        ).applyGroupSeat(template, overrideSeat)
        assertEquals("Reviewer only.", seated.systemPrompt)
        assertEquals(setOf("workspace-python"), seated.enabledSkills)
        assertEquals(setOf(lorebookId), seated.lorebookIds)
        assertEquals(true, seated.enableMemory)
        assertEquals(true, seated.enableWebSearch)
        assertEquals(false, seated.enableSessionMemory)
    }

    @Test
    fun applyGroupSeatEmptySkillsDoNotFallBackToAssistant() {
        val seated = gpt.copy(enabledSkills = setOf("solo-skill"))
            .applyGroupSeat(template, gptSeat)
        assertEquals(emptySet<String>(), seated.enabledSkills)
    }

    @Test
    fun applyGroupSeatEmptyOverrideClearsAssistantPrompt() {
        val seat = gptSeat.copy(overrides = gptSeat.overrides.copy(systemPrompt = ""))
        val seated = gpt.copy(systemPrompt = "Original").applyGroupSeat(template, seat)
        assertEquals("", seated.systemPrompt)
    }

    @Test
    fun clearedMultiMentionStickyFallsToFirstEnabledSeat() {
        val speakers = GroupChatEngine.resolveSpeakerSeatIds(
            userText = "hello everyone",
            template = template,
            assistantsById = assistants,
            stickySeatId = null,
        )
        assertEquals(listOf(gptSeat.id), speakers)
        assertEquals(
            gptSeat.id,
            GroupChatEngine.nextStickySeatId(speakers, previousSticky = null),
        )
    }

    @Test
    fun rewriteLeavesSyntheticSessionMemoryAlone() {
        val names = template.buildSeatDisplayNames(assistants)
        val synthetic = UIMessage.user("## Session Memories").copy(isSynthetic = true)
        val rewritten = GroupChatEngine.rewritePromptMessagesForSeat(
            messages = listOf(synthetic, UIMessage.user("hi")),
            seat = claudeSeat,
            selfAssistantId = claude.id,
            seatDisplayNames = names,
            assistantsById = assistants,
            userName = "Roc",
        )
        assertEquals("## Session Memories", rewritten.first().toText())
        assertTrue(rewritten.first().isSynthetic)
        assertTrue(rewritten.last().toText().contains("[Message from Roc (user)]"))
    }

    @Test
    fun selfMessagesStayAssistant() {
        val names = template.buildSeatDisplayNames(assistants)
        val self = UIMessage.assistant("my earlier note").copy(
            speakerSeatId = claudeSeat.id,
            speakerAssistantId = claude.id,
        )
        val rewritten = GroupChatEngine.rewritePromptMessagesForSeat(
            messages = listOf(UIMessage.user("hi"), self),
            seat = claudeSeat,
            selfAssistantId = claude.id,
            seatDisplayNames = names,
            assistantsById = assistants,
            userName = "Roc",
        )
        assertTrue(rewritten.any { it.role == MessageRole.ASSISTANT && it.speakerSeatId == claudeSeat.id })
    }

    @Test
    fun displaySeatFallsToFirstEnabledWhenStickyDisabled() {
        val disabledGpt = gptSeat.copy(defaultEnabled = false)
        val group = template.copy(seats = listOf(disabledGpt, claudeSeat))
        assertEquals(claudeSeat.id, resolveGroupChatDisplaySeat(group, gptSeat.id)?.id)
        assertEquals(claudeSeat.id, resolveGroupChatDisplaySeat(group, null)?.id)
    }

    @Test
    fun displayModelUsesSeatOverrideNotBareAssistantModel() {
        val overrideModel = Uuid.parse("77777777-7777-7777-7777-777777777777")
        val globalModel = Uuid.parse("88888888-8888-8888-8888-888888888888")
        val seat = gptSeat.copy(overrides = gptSeat.overrides.copy(chatModelId = overrideModel))
        val group = template.copy(seats = listOf(seat, claudeSeat))
        val settled = mapOf(
            gpt.id to gpt.copy(chatModelId = globalModel),
            claude.id to claude,
        )
        assertEquals(
            overrideModel,
            resolveGroupChatModelId(
                template = group,
                stickySpeakerSeatId = seat.id,
                assistantsById = settled,
                globalChatModelId = globalModel,
            ),
        )
    }
}
