package se.kodasmart.smartagent.api.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class Content(
    val role: String,
    val parts: List<Part>
)

@Serializable
data class Part(
    val text: String? = null,
    val functionCall: FunctionCall? = null,
    val functionResponse: FunctionResponse? = null,
    val thoughtSignature: String? = null,
    @SerialName("thought_signature") val thoughtSignatureSnake: String? = null
)

@Serializable
data class FunctionCall(
    val name: String,
    val args: JsonObject,
    val thoughtSignature: String? = null,
    @SerialName("thought_signature") val thoughtSignatureSnake: String? = null
)

@Serializable
data class FunctionResponse(
    val name: String,
    val response: JsonObject
)