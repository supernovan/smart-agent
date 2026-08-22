package se.kodasmart.smartagent.api.models

import kotlinx.serialization.Serializable

@Serializable
data class ModelListResponse(
    val models: List<GeminiModelInfo>? = null,
    val error: GeminiError? = null
)

@Serializable
data class GeminiModelInfo(
    val name: String,
    val displayName: String,
    val supportedGenerationMethods: List<String> = emptyList()
)