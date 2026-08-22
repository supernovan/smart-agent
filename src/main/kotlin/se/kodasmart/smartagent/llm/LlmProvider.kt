package se.kodasmart.smartagent.llm

import se.kodasmart.smartagent.llm.models.LlmMessage
import se.kodasmart.smartagent.llm.models.LlmResponse

interface LlmProvider {
    suspend fun sendMessage(
        history: List<LlmMessage>,
        modelName: String,
        useTools: Boolean = true
    ): LlmResponse

    suspend fun getAvailableModels(): List<String>
}