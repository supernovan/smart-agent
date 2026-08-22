package se.kodasmart.smartagent.core

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import se.kodasmart.smartagent.api.client.GeminiApiClient
import se.kodasmart.smartagent.api.models.FunctionResponse
import se.kodasmart.smartagent.api.models.Part
import se.kodasmart.smartagent.llm.LlmProvider
import se.kodasmart.smartagent.llm.models.LlmMessage
import se.kodasmart.smartagent.llm.models.LlmResponse
import se.kodasmart.smartagent.llm.models.MessageRole
import se.kodasmart.smartagent.tools.CodeWorkspaceTools
import java.io.File

class AgentLoop(
    private val codeWorkspaceTools: CodeWorkspaceTools,
    private val llmProvider: LlmProvider,
) {

    val conversationHistory = mutableListOf<LlmMessage>()

    suspend fun runLoop(
        userMessage: String,
        modelName: String,
        relevantPaths: List<String>,
        maxIterations: Int,
        onStatusUpdate: (String) -> Unit,
        onToolAction: (String) -> Unit,
        onPlanGenerated: suspend (String) -> String?
    ): String {
        val basePath = codeWorkspaceTools.getBasePath() ?: return "Error: Project base path not found."

        var totalPromptTokens = 0
        var totalOutputTokens = 0
        var totalThoughtsTokens = 0
        var totalCachedTokens = 0
        var iterationCount = 0

        try {
            val editorConfig = File(basePath, ".editorconfig")
            val styleRules = if (editorConfig.exists()) {
                """
            CODE STYLE & KTLINT RULES (from .editorconfig):
            ${editorConfig.readText().take(2000)}
            """.trimIndent()
            } else {
                "CODE STYLE: Follow standard Kotlin conventions."
            }

            val geminiAgentDir = File(basePath, ".gemini-agent")
            val rulesBuilder = java.lang.StringBuilder()

            if (geminiAgentDir.exists() && geminiAgentDir.isDirectory) {
                val ruleFiles = geminiAgentDir.listFiles { file -> file.isFile && !file.isHidden }
                ruleFiles?.forEach { file ->
                    rulesBuilder.append("\n--- Rules from ${file.name} ---\n")
                    rulesBuilder.append(file.readText().take(3000))
                    rulesBuilder.append("\n")
                }
            }

            val customRules = if (rulesBuilder.isNotEmpty()) {
                "PROJECT SPECIFIC RULES:\n$rulesBuilder"
            } else ""

            val repoMap = codeWorkspaceTools.generateRepoMap(relevantPaths)

            val baseContext = """
            You are an elite, laser-focused senior Kotlin/Spring Boot engineer.
            
            $styleRules
            $customRules
            
            PROJECT FILE STRUCTURE (Scope: ${if (relevantPaths.isEmpty()) "ALL" else "FILTERED"}):
            $repoMap
        """.trimIndent()

            onStatusUpdate("Thinking about creating a plan...")


            val planningPrompt = """
            $baseContext
            
            USER REQUEST:
            $userMessage
            
            TASK: Write a concise, step-by-step plan to solve the user's request. 
            
            CRITICAL RULES FOR YOUR PLAN:
            1. Specify EXACT file names only (e.g., 'SalaryService.kt'), NOT full paths. Do not include 'src/main/...' or package names in the file references.
            2. Verify that the file names you propose actually exist in the PROJECT FILE STRUCTURE.
            3. Do NOT write the actual source code. Just write the logical steps.
            """.trimIndent()

            val planningHistory = listOf(
                LlmMessage(
                    role = MessageRole.USER,
                    text = planningPrompt
                )
            )

            val planResponse = llmProvider.sendMessage(
                history = planningHistory,
                modelName = modelName,
                useTools = false,
            )
            val planText = planResponse.text
                ?: throw Exception("Agent failed to generate plan.")

            fun trackUsage(response: LlmResponse) {
                response.usage?.let { meta ->
                    totalPromptTokens += meta.promptTokens
                    totalOutputTokens += meta.completionTokens
                    totalThoughtsTokens += meta.thinkingTokens
                    totalCachedTokens += meta.cachedTokens
                }
            }

            trackUsage(planResponse)

            onStatusUpdate("Waiting for approval of the plan...")
            val approvedPlan =
                onPlanGenerated(planText) ?: return "The user cancelled the plan."

            onToolAction("📋 **Approved Plan:**\n$approvedPlan")
            onStatusUpdate("Executing said plan...")

            val executionSystemPrompt = """
            $baseContext
            
            RULES FOR EXECUTION:
            1. When using tools like 'getFileContent' or 'insertCode', provide ONLY the exact file name (e.g., 'SalaryService.kt').
            2. Stick strictly to the approved plan below.
            3. Minimize tool calls. Directly read the files you need, then perform the changes immediately.
            4. When creating or modifying code, strictly follow the CODE STYLE and PROJECT SPECIFIC RULES.
            5. CRITICAL: Once you have successfully executed the final step in the plan, STOP immediately. Do NOT re-read files to double-check your work. Output a text summary of what you did to end the execution.
            
            --- APPROVED PLAN ---
            $approvedPlan
            ---------------------
        """.trimIndent()

            conversationHistory.clear()
            conversationHistory.add(
                LlmMessage(
                    role = MessageRole.USER,
                    text = executionSystemPrompt
                )
            )
            conversationHistory.add(
                LlmMessage(
                    role = MessageRole.USER,
                    text = userMessage
                )
            )

            var isTaskComplete = false

            while (iterationCount < maxIterations) {
                iterationCount++
                onStatusUpdate("Waiting for a response from $modelName... (Steg $iterationCount/$maxIterations)")

                val modelResponse = llmProvider.sendMessage(conversationHistory, modelName)

                if (modelResponse.text == null && modelResponse.toolCalls.isEmpty()) {
                    throw Exception("API did not return any text or tool calls.")
                }

                conversationHistory.add(
                    LlmMessage(
                        role = MessageRole.ASSISTANT,
                        text = modelResponse.text,
                        toolCalls = modelResponse.toolCalls
                    )
                )


                trackUsage(modelResponse)

                if (modelResponse.toolCalls.isNotEmpty()) {
                    val toolCall = modelResponse.toolCalls.first()
                    val functionName = toolCall.name
                    val args = toolCall.arguments

                    val argsString = args.map { "${it.key}: ${it.value}" }.joinToString(", ")
                    onToolAction("> Agent called for: `$functionName($argsString)`")

                    val targetFile = args["fileName"] ?: args["relativePath"] ?: "projektet"

                    when (functionName) {
                        "getFileContent" -> onStatusUpdate("Reading the file $targetFile...")
                        "replaceCodeBlock", "insertCode" -> onStatusUpdate("Modifying code in $targetFile...")
                        "createFile" -> onStatusUpdate("Creating the file $targetFile...")
                        else -> onStatusUpdate("Running tool: $functionName...")
                    }

                    val resultString = executeFunctionCall(functionName, args)

                    val shortResult =
                        if (resultString.length > 100) resultString.substring(0, 100) + "..." else resultString
                    onToolAction("> Response from IntelliJ: *$shortResult*")

                    conversationHistory.add(
                        LlmMessage(
                            role = MessageRole.TOOL,
                            toolName = functionName,
                            toolResult = resultString
                        )
                    )

                } else if (modelResponse.text != null) {
                    isTaskComplete = true
                    return modelResponse.text
                }
            }

            return "Agent got interrupted. Reached the max iteration limit $maxIterations before it was able to finish."
        } finally {
            val summary = """
                ** Summary:**
                * **Steps executed:** $iterationCount
                * **Input Tokens:** $totalPromptTokens (where cached tokens $totalCachedTokens)
                * **Output Tokens:** $totalOutputTokens
                * **Thinking Tokens:** $totalThoughtsTokens
            """.trimIndent()

            onToolAction(summary)
        }
    }

    private fun executeFunctionCall(functionName: String, args: Map<String, String>): String {
        return try {
            when (functionName) {
                "getFileContent" -> {
                    val fileName = args["fileName"]
                        ?: throw IllegalArgumentException("Missing fileName")
                    codeWorkspaceTools.getFileContent(fileName)
                }

                "insertCode" -> {
                    val fileName = args["fileName"]
                        ?: throw IllegalArgumentException("Missing fileName")
                    val offset = args["offset"]?.toIntOrNull()
                        ?: throw IllegalArgumentException("Missing or invalid offset")
                    val newCode = args["newCode"]
                        ?: throw IllegalArgumentException("Missing newCode")

                    codeWorkspaceTools.insertCode(fileName, offset, newCode)
                }

                "replaceCodeBlock" -> {
                    val fileName = args["fileName"]
                        ?: throw IllegalArgumentException("Missing fileName")
                    val searchString = args["searchString"]
                        ?: throw IllegalArgumentException("Missing searchString")
                    val replacementString = args["replacementString"]
                        ?: throw IllegalArgumentException("Missing replacementString")

                    codeWorkspaceTools.replaceCodeBlock(fileName, searchString, replacementString)
                    "Success: Code block replaced in $fileName."
                }

                "createFile" -> {
                    val path = args["relativePath"]
                        ?: throw IllegalArgumentException("Missing relativePath")
                    val content = args["content"]
                        ?: throw IllegalArgumentException("Missing content")

                    codeWorkspaceTools.createFile(path, content)
                }

                "listDirectory" -> {
                    val path = args["directoryPath"]
                        ?: throw IllegalArgumentException("Missing directoryPath")
                    codeWorkspaceTools.listDirectory(path)
                }

                else -> "Error: Unknown function $functionName"
            }
        } catch (e: Exception) {
            "Error executing $functionName: ${e.message}"
        }
    }
}