package se.kodasmart.smartagent.core

import se.kodasmart.smartagent.llm.LlmProvider
import se.kodasmart.smartagent.llm.models.LlmMessage
import se.kodasmart.smartagent.llm.models.LlmResponse
import se.kodasmart.smartagent.llm.models.MessageRole
import se.kodasmart.smartagent.tools.CodeWorkspaceTools

class GeminiAgentLoop(
    codeWorkspaceTools: CodeWorkspaceTools,
    llmProvider: LlmProvider
) : AgentLoop(codeWorkspaceTools, llmProvider) {

    override fun getExecutionRules(): String {
        return loadPromptFromFile("gemini/execution-rules.md")
    }

    override fun buildInitialExecutionHistory(systemPrompt: String, userMessage: String): List<LlmMessage> {
        return listOf(
            LlmMessage(role = MessageRole.SYSTEM, text = systemPrompt),
            LlmMessage(role = MessageRole.USER, text = userMessage)
        )
    }

    override fun handleTextResponse(
        modelResponse: LlmResponse,
        iterationCount: Int,
        maxIterations: Int,
        onToolAction: (String) -> Unit
    ): Boolean {
        return true
    }
}