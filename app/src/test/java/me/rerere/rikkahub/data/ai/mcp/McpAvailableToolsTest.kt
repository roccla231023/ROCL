package me.rerere.rikkahub.data.ai.mcp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.uuid.Uuid

class McpAvailableToolsTest {
    private val searchServerId = Uuid.parse("11111111-1111-1111-1111-111111111111")
    private val timeServerId = Uuid.parse("22222222-2222-2222-2222-222222222222")
    private val disabledServerId = Uuid.parse("33333333-3333-3333-3333-333333333333")

    private val servers = listOf(
        McpServerConfig.StreamableHTTPServer(
            id = searchServerId,
            commonOptions = McpCommonOptions(
                enable = true,
                name = "search",
                tools = listOf(
                    McpTool(name = "web_search", enable = true),
                    McpTool(name = "web_fetch", enable = false),
                ),
            ),
            url = "https://example.com/search",
        ),
        McpServerConfig.StreamableHTTPServer(
            id = timeServerId,
            commonOptions = McpCommonOptions(
                enable = true,
                name = "time",
                tools = listOf(McpTool(name = "now", enable = true)),
            ),
            url = "https://example.com/time",
        ),
        McpServerConfig.StreamableHTTPServer(
            id = disabledServerId,
            commonOptions = McpCommonOptions(
                enable = false,
                name = "disabled",
                tools = listOf(McpTool(name = "secret", enable = true)),
            ),
            url = "https://example.com/disabled",
        ),
    )

    @Test
    fun emptyIdsYieldNoTools() {
        val tools = filterAvailableMcpTools(servers, emptySet())
        assertTrue(tools.isEmpty())
    }

    @Test
    fun onlySelectedEnabledServerToolsAreKept() {
        val tools = filterAvailableMcpTools(servers, setOf(searchServerId, disabledServerId))
        assertEquals(listOf("web_search"), tools.map { it.third.name })
        assertEquals(listOf(searchServerId), tools.map { it.first })
        assertEquals(listOf("search"), tools.map { it.second })
    }

    @Test
    fun anotherAssistantDoesNotReceiveUnselectedServers() {
        val firstAssistant = filterAvailableMcpTools(servers, setOf(searchServerId)).map { it.third.name }
        val seat = filterAvailableMcpTools(servers, emptySet()).map { it.third.name }
        assertEquals(listOf("web_search"), firstAssistant)
        assertTrue(seat.isEmpty())
    }
}
