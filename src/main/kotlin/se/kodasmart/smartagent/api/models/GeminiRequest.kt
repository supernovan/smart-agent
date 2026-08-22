package se.kodasmart.smartagent.api.models

import kotlinx.serialization.Serializable

@Serializable
data class GeminiRequest(
    val contents: List<Content>,
    val tools: List<ToolDeclaration>? = null,
    val safetySettings: List<SafetySetting> = listOf(
        SafetySetting("HARM_CATEGORY_HARASSMENT", "BLOCK_NONE"),
        SafetySetting("HARM_CATEGORY_HATE_SPEECH", "BLOCK_NONE"),
        SafetySetting("HARM_CATEGORY_SEXUALLY_EXPLICIT", "BLOCK_NONE"),
        SafetySetting("HARM_CATEGORY_DANGEROUS_CONTENT", "BLOCK_NONE")
    )
)

@Serializable
data class SafetySetting(
    val category: String,
    val threshold: String
)

@Serializable
data class ToolDeclaration(
    val functionDeclarations: List<FunctionDeclaration>
)

@Serializable
data class GeminiResponse(
    val candidates: List<Candidate>? = null,
    val error: GeminiError? = null,
    val usageMetadata: UsageMetadata? = null
)

@Serializable
data class GeminiError(
    val code: Int? = null,
    val message: String? = null,
    val status: String? = null
)

@Serializable
data class Candidate(
    val content: Content,
    val finishReason: String? = null
)

@Serializable
data class UsageMetadata(
    val promptTokenCount: Int? = 0,
    val candidatesTokenCount: Int? = 0,
    val totalTokenCount: Int? = 0,
    val cachedContentTokenCount: Int? = 0,
    val thoughtsTokenCount: Int? = 0
)