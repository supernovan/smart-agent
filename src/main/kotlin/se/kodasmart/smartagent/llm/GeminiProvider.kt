package se.kodasmart.smartagent.llm

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import se.kodasmart.smartagent.api.client.GeminiApiClient
import se.kodasmart.smartagent.api.models.Content
import se.kodasmart.smartagent.api.models.FunctionCall
import se.kodasmart.smartagent.api.models.FunctionResponse
import se.kodasmart.smartagent.api.models.GeminiResponse
import se.kodasmart.smartagent.api.models.GeminiToolsSchema
import se.kodasmart.smartagent.api.models.Part
import se.kodasmart.smartagent.llm.models.LlmMessage
import se.kodasmart.smartagent.llm.models.LlmResponse
import se.kodasmart.smartagent.llm.models.LlmToolCall
import se.kodasmart.smartagent.llm.models.LlmUsage
import se.kodasmart.smartagent.llm.models.MessageRole

class GeminiProvider(
    private val apiKey: String
) : LlmProvider {

    private val apiClient = GeminiApiClient(apiKey = apiKey)

    override suspend fun sendMessage(
        history: List<LlmMessage>,
        modelName: String,
        useTools: Boolean,
    ): LlmResponse {
        val geminiContents = history.map { it.toGeminiContent() }
        val toolsToUse = if (useTools) GeminiToolsSchema.availableTools else emptyList()
        val response = apiClient.sendMessage(
            history = geminiContents,
            modelName = modelName,
            tools = toolsToUse
        )
        return response.toLlmResponse()
    }

    override suspend fun getAvailableModels(): List<String> {
        return apiClient.getAvailableModels()
    }

    private fun LlmMessage.toGeminiContent(): Content {
        val parts = mutableListOf<Part>()

        if (text != null) {
            parts.add(Part(text = text))
        }

        if (toolCalls.isNotEmpty()) {
            toolCalls.forEach { call ->
                parts.add(
                    Part(
                        functionCall = FunctionCall(
                            name = call.name,
                            args = JsonObject(call.arguments.mapValues { JsonPrimitive(it.value) })
                        )
                    )
                )
            }
        }

        if (role == MessageRole.TOOL && toolName != null && toolResult != null) {
            parts.add(
                Part(
                    functionResponse = FunctionResponse(
                        name = toolName,
                        response = JsonObject(mapOf("result" to JsonPrimitive(toolResult)))
                    )
                )
            )
        }

        val geminiRole = when (role) {
            MessageRole.USER -> "user"
            MessageRole.ASSISTANT -> "model"
            MessageRole.TOOL -> "function"
            MessageRole.SYSTEM -> "user"
        }

        return Content(role = geminiRole, parts = parts)
    }

    private fun GeminiResponse.toLlmResponse(): LlmResponse {
        val parts = this.candidates?.firstOrNull()?.content?.parts ?: emptyList()

        val textParts = parts.mapNotNull { it.text }
        val combinedText = if (textParts.isNotEmpty()) textParts.joinToString("\n") else null

        val extractedToolCalls = parts.mapNotNull { part ->
            part.functionCall?.let { fc ->
                LlmToolCall(
                    name = fc.name,
                    // Översätt kotlinx.serialization JsonObject tillbaka till vår enkla Map<String, String>
                    arguments = fc.args.mapValues { it.value.jsonPrimitive.content }
                )
            }
        }

        val extractedUsage = this.usageMetadata?.let { meta ->
            LlmUsage(
                promptTokens = meta.promptTokenCount ?: 0,
                completionTokens = meta.candidatesTokenCount ?: 0,
                thinkingTokens = meta.thoughtsTokenCount ?: 0,
                cachedTokens = meta.cachedContentTokenCount ?: 0
            )
        }

        return LlmResponse(
            text = combinedText,
            toolCalls = extractedToolCalls,
            usage = extractedUsage
        )
    }
}