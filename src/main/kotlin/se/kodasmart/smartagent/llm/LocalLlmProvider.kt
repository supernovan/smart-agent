package se.kodasmart.smartagent.llm

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import se.kodasmart.smartagent.api.models.GeminiToolsSchema
import se.kodasmart.smartagent.llm.models.LlmMessage
import se.kodasmart.smartagent.llm.models.LlmResponse
import se.kodasmart.smartagent.llm.models.LlmToolCall
import se.kodasmart.smartagent.llm.models.LlmUsage
import se.kodasmart.smartagent.llm.models.MessageRole

class LocalLlmProvider(private val baseUrl: String) : LlmProvider {

    private val httpClient = HttpClient(CIO) {
        install(HttpTimeout) {
            requestTimeoutMillis = 120_000
            connectTimeoutMillis = 15_000
            socketTimeoutMillis = 120_000
        }
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    override suspend fun getAvailableModels(): List<String> {
        return try {
            val response: JsonObject = httpClient.get("$baseUrl/models").body()
            val dataArray = response["data"]?.jsonArray
            dataArray?.mapNotNull { it.jsonObject["id"]?.jsonPrimitive?.content } ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun cleanSchemaForOpenAI(element: JsonElement): JsonElement {
        if (element is JsonObject) {
            return buildJsonObject {
                element.forEach { (key, value) ->
                    val isAllowedOpenAiField = key in listOf("type", "properties", "required", "description", "enum", "items")
                    if (isAllowedOpenAiField) {
                        if (key == "type" && value is JsonPrimitive && value.isString) {
                            put(key, value.content.lowercase())
                        } else {
                            put(key, cleanSchemaForOpenAI(value))
                        }
                    }
                }
            }
        } else if (element is kotlinx.serialization.json.JsonArray) {
            return buildJsonArray {
                element.forEach { add(cleanSchemaForOpenAI(it)) }
            }
        }
        return element
    }

    override suspend fun sendMessage(history: List<LlmMessage>, modelName: String, useTools: Boolean): LlmResponse {
        val payload = buildJsonObject {
            put("model", modelName)

            if (useTools && GeminiToolsSchema.availableTools.isNotEmpty()) {
                putJsonArray("tools") {
                    GeminiToolsSchema.availableTools.forEach { tool ->
                        addJsonObject {
                            put("type", "function")
                            putJsonObject("function") {
                                put("name", tool.name)
                                put("description", tool.description ?: "")

                                if (tool.parameters != null) {
                                    val paramsJson = Json.encodeToJsonElement(tool.parameters).jsonObject
                                    put("parameters", cleanSchemaForOpenAI(paramsJson))
                                } else {
                                    putJsonObject("parameters") {
                                        put("type", "object")
                                        putJsonObject("properties") {}
                                    }
                                }
                            }
                        }
                    }
                }
            }

            putJsonArray("messages") {
                history.forEach { msg ->
                    addJsonObject {
                        val roleStr = when (msg.role) {
                            MessageRole.USER -> "user"
                            MessageRole.ASSISTANT -> "assistant"
                            MessageRole.SYSTEM -> "system"
                            MessageRole.TOOL -> "tool"
                        }
                        put("role", roleStr)

                        if (msg.role == MessageRole.TOOL) {
                            put("tool_call_id", msg.toolCallId ?: "unknown")
                            put("content", msg.toolResult ?: "")
                        } else {
                            if (msg.text != null) {
                                put("content", msg.text)
                            } else if (msg.toolCalls.isEmpty()) {
                                put("content", "")
                            }

                            if (msg.role == MessageRole.ASSISTANT && msg.toolCalls.isNotEmpty()) {
                                putJsonArray("tool_calls") {
                                    msg.toolCalls.forEach { call ->
                                        addJsonObject {
                                            put("id", call.thoughtSignature ?: call.name)
                                            put("type", "function")
                                            putJsonObject("function") {
                                                put("name", call.name)
                                                val argsObj = JsonObject(call.arguments.mapValues { JsonPrimitive(it.value) })
                                                put("arguments", argsObj.toString())
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        val response: JsonObject = httpClient.post("$baseUrl/chat/completions") {
            contentType(ContentType.Application.Json)
            setBody(payload)
        }.body()

        val choice = response["choices"]?.jsonArray?.firstOrNull()?.jsonObject
        val message = choice?.get("message")?.jsonObject

        // 1. SÄKER HANTERING AV CONTENT: Undviker att JsonNull blir strängen "null"
        val contentElement = message?.get("content")
        val rawContent = if (contentElement == null || contentElement is JsonNull) {
            null
        } else {
            contentElement.jsonPrimitive.content
        }

        var finalContent = if (rawContent == "null") null else rawContent

        val toolCallsArray = message?.get("tool_calls")?.jsonArray
        val extractedToolCalls = toolCallsArray?.mapNotNull { tcElement ->
            val tcObj = tcElement.jsonObject
            val id = tcObj["id"]?.jsonPrimitive?.content
            val functionObj = tcObj["function"]?.jsonObject

            val name = functionObj?.get("name")?.jsonPrimitive?.content ?: return@mapNotNull null

            val argumentsString = functionObj["arguments"]?.jsonPrimitive?.content ?: "{}"
            val argumentsMap = try {
                val parsedJson = Json.parseToJsonElement(argumentsString).jsonObject
                parsedJson.mapValues { it.value.jsonPrimitive.content }
            } catch (e: Exception) {
                emptyMap()
            }

            LlmToolCall(
                name = name,
                arguments = argumentsMap,
                thoughtSignature = id
            )
        }?.toMutableList() ?: mutableListOf()

        // 2. FALLBACK ENBART OM VERKTYG ÄR TILLÅTNA
        if (useTools && extractedToolCalls.isEmpty() && finalContent != null) {
            val contentTrimmed = finalContent.trim()
            if (contentTrimmed.startsWith("{") && contentTrimmed.endsWith("}")) {
                try {
                    val fallbackJson = Json.parseToJsonElement(contentTrimmed).jsonObject
                    val functionName = fallbackJson["name"]?.jsonPrimitive?.content
                    val argumentsObj = fallbackJson["arguments"]?.jsonObject

                    if (functionName != null && argumentsObj != null) {
                        val parsedArgs = argumentsObj.mapValues { it.value.jsonPrimitive.content }

                        extractedToolCalls.add(
                            LlmToolCall(
                                name = functionName,
                                arguments = parsedArgs,
                                thoughtSignature = "fallback_id_${System.currentTimeMillis()}"
                            )
                        )
                        finalContent = null
                    }
                } catch (e: Exception) { }
            }
        }

        val usageObj = response["usage"]?.jsonObject
        val extractedUsage = LlmUsage(
            promptTokens = usageObj?.get("prompt_tokens")?.jsonPrimitive?.int ?: 0,
            completionTokens = usageObj?.get("completion_tokens")?.jsonPrimitive?.int ?: 0,
            thinkingTokens = 0,
            cachedTokens = 0
        )

        return LlmResponse(
            text = finalContent,
            toolCalls = extractedToolCalls,
            usage = extractedUsage
        )
    }
}