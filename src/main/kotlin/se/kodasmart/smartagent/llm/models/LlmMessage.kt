package se.kodasmart.smartagent.llm.models

data class LlmMessage(
    val role: MessageRole,
    val text: String? = null,
    val toolCalls: List<LlmToolCall> = emptyList(),
    val toolResult: String? = null,
    val toolName: String? = null,
    val toolCallId: String? = null,
)

data class LlmToolCall(
    val name: String,
    val arguments: Map<String, String>,
    val thoughtSignature: String? = null
)

data class LlmUsage(
    val promptTokens: Int = 0,
    val completionTokens: Int = 0,
    val thinkingTokens: Int = 0,
    val cachedTokens: Int = 0
)

enum class MessageRole {
    USER,
    ASSISTANT,
    TOOL,
    SYSTEM
}

data class LlmResponse(
    val text: String? = null,
    val toolCalls: List<LlmToolCall> = emptyList(),
    val usage: LlmUsage? = null
)