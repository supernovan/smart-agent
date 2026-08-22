package se.kodasmart.smartagent.core

import se.kodasmart.smartagent.llm.LlmProvider
import se.kodasmart.smartagent.llm.models.LlmMessage
import se.kodasmart.smartagent.llm.models.LlmResponse
import se.kodasmart.smartagent.llm.models.MessageRole
import se.kodasmart.smartagent.tools.CodeWorkspaceTools

class LocalAgentLoop(
    codeWorkspaceTools: CodeWorkspaceTools,
    llmProvider: LlmProvider
) : AgentLoop(codeWorkspaceTools, llmProvider) {

    override fun getExecutionRules(): String {
        return loadPromptFromFile("local/execution-rules.md")
    }

    override fun buildInitialExecutionHistory(systemPrompt: String, userMessage: String): List<LlmMessage> {
        return listOf(
            LlmMessage(role = MessageRole.SYSTEM, text = systemPrompt),
            LlmMessage(
                role = MessageRole.USER,
                text = "Original request: $userMessage\n\nPlease begin executing the plan now. You MUST output a tool call to perform the first step. Do not just reply with text."
            )
        )
    }

    override fun executeFunctionCall(functionName: String, args: Map<String, String>): String {
        return try {
            fun extractFileName(raw: String): String {
                return raw.substringAfterLast("/").substringAfterLast("\\")
            }

            when (functionName) {
                "getFileContent" -> {
                    val rawFileName = args["fileName"] ?: args["file"] ?: args["path"]
                    ?: throw IllegalArgumentException("Missing 'fileName'. Keys provided: ${args.keys}")
                    codeWorkspaceTools.getFileContent(extractFileName(rawFileName))
                }
                "insertCode" -> {
                    val rawFileName = args["fileName"] ?: args["file"] ?: args["path"]
                    ?: throw IllegalArgumentException("Missing 'fileName'. Keys provided: ${args.keys}")
                    val offset = args["offset"]?.toIntOrNull()
                        ?: throw IllegalArgumentException("Missing/invalid 'offset'. Keys provided: ${args.keys}")
                    val newCode = args["newCode"] ?: args["code"] ?: args["content"]
                    ?: throw IllegalArgumentException("Missing 'newCode'. Keys provided: ${args.keys}")

                    codeWorkspaceTools.insertCode(extractFileName(rawFileName), offset, newCode)
                }
                "replaceCodeBlock" -> {
                    val rawFileName = args["fileName"] ?: args["file"] ?: args["path"]
                    ?: throw IllegalArgumentException("Missing 'fileName'. Keys provided: ${args.keys}")
                    val searchString = args["searchString"] ?: args["search"]
                    ?: throw IllegalArgumentException("Missing 'searchString'. Keys provided: ${args.keys}")
                    val replacementString = args["replacementString"] ?: args["replace"] ?: args["newCode"]
                    ?: throw IllegalArgumentException("Missing 'replacementString'. Keys provided: ${args.keys}")

                    codeWorkspaceTools.replaceCodeBlock(extractFileName(rawFileName), searchString, replacementString)
                    "Success: Code block replaced in ${extractFileName(rawFileName)}."
                }
                "createFile" -> {
                    val path = args["relativePath"] ?: args["path"] ?: args["file"] ?: args["fileName"]
                    ?: throw IllegalArgumentException("Missing 'relativePath'. Keys provided: ${args.keys}")
                    val content = args["content"] ?: args["code"] ?: args["text"]
                    ?: throw IllegalArgumentException("Missing 'content'. Keys provided: ${args.keys}")

                    codeWorkspaceTools.createFile(path, content)
                }
                "listDirectory" -> {
                    val path = args["directoryPath"] ?: args["path"] ?: args["dir"] ?: args["directory"]
                    ?: throw IllegalArgumentException("Missing 'directoryPath'. Keys provided: ${args.keys}")
                    codeWorkspaceTools.listDirectory(path)
                }
                else -> "Error: Unknown function $functionName"
            }
        } catch (e: Exception) {
            "Error executing $functionName: ${e.message}"
        }
    }

    override fun handleTextResponse(
        modelResponse: LlmResponse,
        iterationCount: Int,
        maxIterations: Int,
        onToolAction: (String) -> Unit
    ): Boolean {
        val textLower = modelResponse.text?.lowercase() ?: ""

        if (textLower.contains("task complete")) {
            return true
        }

        onToolAction("> Agent responded with text. Nudging it to use tools...")
        conversationHistory.add(
            LlmMessage(
                role = MessageRole.USER,
                text = "You replied with plain text. You MUST invoke a tool (like `createFile` or `getFileContent`) to progress the plan. Do not explain your steps. If you are completely done, output exactly 'TASK COMPLETE'."
            )
        )

        return false
    }
}