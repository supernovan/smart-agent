package se.kodasmart.smartagent.api.models

import kotlinx.serialization.Serializable

@Serializable
data class FunctionDeclaration(
    val name: String,
    val description: String,
    val parameters: Schema
)

@Serializable
data class Schema(
    val type: String,
    val properties: Map<String, SchemaProperty> = emptyMap(),
    val required: List<String> = emptyList()
)

@Serializable
data class SchemaProperty(
    val type: String,
    val description: String
)