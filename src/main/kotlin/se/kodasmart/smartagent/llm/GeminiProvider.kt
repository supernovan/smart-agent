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

class GeminiProvider(private val apiKey: String, private val timeoutSeconds: Int = 120) : LlmProvider {

    private val apiClient = GeminiApiClient(apiKey = apiKey, timeoutSeconds = timeoutSeconds)

    override suspend fun sendMessage(
        history: List<LlmMessage>,
        modelName: String,
        useTools: Boolean,
    ): LlmResponse {
        val rawGeminiContents = mutableListOf<Content>()
        var currentToolParts = mutableListOf<Part>()

        for (msg in history) {
            if (msg.role == MessageRole.TOOL && msg.toolName != null && msg.toolResult != null) {
                currentToolParts.add(
                    Part(
                        functionResponse = FunctionResponse(
                            name = msg.toolName,
                            response = JsonObject(mapOf("result" to JsonPrimitive(msg.toolResult)))
                        )
                    )
                )
            } else {
                if (currentToolParts.isNotEmpty()) {
                    rawGeminiContents.add(Content(role = "user", parts = currentToolParts.toList()))
                    currentToolParts.clear()
                }

                val parts = mutableListOf<Part>()

                if (!msg.text.isNullOrBlank()) {
                    parts.add(Part(text = msg.text))
                }

                if (msg.toolCalls.isNotEmpty()) {
                    msg.toolCalls.forEach { call ->
                        parts.add(
                            Part(
                                functionCall = FunctionCall(
                                    name = call.name,
                                    args = JsonObject(call.arguments.mapValues { JsonPrimitive(it.value) })
                                ),
                                thoughtSignatureSnake = call.thoughtSignature,
                                thoughtSignature = call.thoughtSignature
                            )
                        )
                    }
                }

                val geminiRole = when (msg.role) {
                    MessageRole.USER, MessageRole.SYSTEM -> "user"
                    MessageRole.ASSISTANT -> "model"
                    else -> "user"
                }

                if (parts.isNotEmpty()) {
                    rawGeminiContents.add(Content(role = geminiRole, parts = parts))
                }
            }
        }

        if (currentToolParts.isNotEmpty()) {
            rawGeminiContents.add(Content(role = "user", parts = currentToolParts.toList()))
        }

        val finalGeminiContents = mutableListOf<Content>()
        for (content in rawGeminiContents) {
            val last = finalGeminiContents.lastOrNull()
            if (last != null && last.role == content.role) {
                finalGeminiContents[finalGeminiContents.lastIndex] = Content(
                    role = last.role,
                    parts = last.parts + content.parts
                )
            } else {
                finalGeminiContents.add(content)
            }
        }

        val toolsToUse = if (useTools) GeminiToolsSchema.availableTools else emptyList()
        val response = apiClient.sendMessage(
            history = finalGeminiContents,
            modelName = modelName,
            tools = toolsToUse
        )
        return response.toLlmResponse()
    }

    override suspend fun getAvailableModels(): List<String> {
        return apiClient.getAvailableModels()
    }

    private fun GeminiResponse.toLlmResponse(): LlmResponse {
        val candidate = this.candidates?.firstOrNull()
        val parts = candidate?.content?.parts ?: emptyList()

        val textParts = parts.mapNotNull { it.text }.filter { it.isNotBlank() }
        val combinedText = if (textParts.isNotEmpty()) textParts.joinToString("\n") else null

        val extractedToolCalls = parts.mapNotNull { part ->
            part.functionCall?.let { fc ->
                LlmToolCall(
                    name = fc.name,
                    arguments = fc.args.mapValues { it.value.jsonPrimitive.content },
                    thoughtSignature = part.thoughtSignature ?: part.thoughtSignatureSnake
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

        if (combinedText == null && extractedToolCalls.isEmpty()) {
            val finishReason = candidate?.finishReason ?: "UNKNOWN"
            if (finishReason != "STOP") {
                throw Exception("Gemini avbröt genereringen. Orsak: $finishReason")
            } else {
                throw Exception("Gemini returnerade varken text eller verktygsanrop (finishReason: STOP).")
            }
        }

        return LlmResponse(
            text = combinedText,
            toolCalls = extractedToolCalls,
            usage = extractedUsage
        )
    }
}