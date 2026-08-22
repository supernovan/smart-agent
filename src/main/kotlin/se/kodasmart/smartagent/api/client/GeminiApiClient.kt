package se.kodasmart.smartagent.api.client

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import se.kodasmart.smartagent.api.models.Content
import se.kodasmart.smartagent.api.models.GeminiRequest
import se.kodasmart.smartagent.api.models.GeminiResponse
import se.kodasmart.smartagent.api.models.GeminiToolsSchema
import se.kodasmart.smartagent.api.models.ModelListResponse
import se.kodasmart.smartagent.api.models.ToolDeclaration
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.LogLevel
import se.kodasmart.smartagent.api.models.FunctionDeclaration

class GeminiApiClient(private val apiKey: String) {

    private val httpClient = HttpClient(CIO) {
        install(HttpTimeout) {
            requestTimeoutMillis = 120_000
            connectTimeoutMillis = 15_000
            socketTimeoutMillis = 120_000
        }
        install(Logging) {
            logger = object : Logger {
                override fun log(message: String) {
                    println("KTOR-LOGG: $message")
                }
            }
            level = LogLevel.ALL
        }
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                encodeDefaults = false
            })
        }
    }

    private val baseUrl = "https://generativelanguage.googleapis.com/v1beta/models"

    suspend fun getAvailableModels(): List<String> {
        val response: ModelListResponse = httpClient.get(baseUrl) {
            header("x-goog-api-key", apiKey)
        }.body()

        if (response.error != null) {
            throw Exception("Failed to fetch models: ${response.error.message}")
        }

        return response.models
            ?.filter { it.supportedGenerationMethods.contains("generateContent") }
            ?.map { it.name.removePrefix("models/") }
            ?: emptyList()
    }

    suspend fun sendMessage(
        history: List<Content>,
        modelName: String,
        tools: List<FunctionDeclaration> = GeminiToolsSchema.availableTools
    ): GeminiResponse {

        val activeTools = if (tools.isEmpty()) {
            null
        } else {
            listOf(ToolDeclaration(functionDeclarations = tools))
        }

        val requestBody = GeminiRequest(
            contents = history,
            tools = activeTools
        )

        return httpClient.post("$baseUrl/$modelName:generateContent") {
            header("x-goog-api-key", apiKey)
            contentType(ContentType.Application.Json)
            setBody(requestBody)
        }.body()
    }
    
    fun close() {
        httpClient.close()
    }
}