package se.kodasmart.smartagent.toolWindow

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.components.JBTabbedPane
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import se.kodasmart.smartagent.toolWindow.ui.ChatPanel
import se.kodasmart.smartagent.toolWindow.ui.SettingsPanel
import se.kodasmart.smartagent.toolWindow.ui.SourcesPanel
import java.awt.BorderLayout
import java.io.File
import javax.swing.JPanel

class AgentToolWindowFactory : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        toolWindow.stripeTitle = "Smart Agent"
        val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        initializeDefaultAgentFiles(project)

        val sourcesPanel = SourcesPanel(project)
        val chatPanel = ChatPanel(project, coroutineScope, sourcesPanel)
        val settingsPanel = SettingsPanel(coroutineScope, onSettingsSaved = {
            chatPanel.loadModels()
        })

        val tabbedPane = JBTabbedPane().apply {
            addTab("Chat", chatPanel.component)
            addTab("Sources", sourcesPanel.component)
            addTab("Settings", settingsPanel.component)
        }

        val mainPanel = JPanel(BorderLayout()).apply {
            add(tabbedPane, BorderLayout.CENTER)
        }

        val contentFactory = ContentFactory.getInstance()
        val content = contentFactory.createContent(mainPanel, "", false)
        toolWindow.contentManager.addContent(content)
    }

    private fun initializeDefaultAgentFiles(project: Project) {
        val basePath = project.basePath ?: return
        val agentDir = File(basePath, ".smart-agent")

        if (!agentDir.exists()) agentDir.mkdirs()
        val geminiDir = File(agentDir, "gemini").apply { if (!exists()) mkdirs() }
        val localDir = File(agentDir, "local").apply { if (!exists()) mkdirs() }

        val planningRulesFile = File(agentDir, "planning-rules.md")
        if (!planningRulesFile.exists()) {
            planningRulesFile.writeText("""
                CRITICAL RULES FOR YOUR PLAN:
                1. For MODIFYING existing files, use EXACT file names only (e.g., 'SalaryService.kt'), without paths.
                2. For CREATING new files, specify the FULL relative path from the project root.
                3. If you cannot see the exact target directory in the PROJECT FILE STRUCTURE, your plan MUST include a step to use `listDirectory` to find the correct path before creating the file.
                4. Do NOT write the actual source code in the plan. Just write the logical steps.
            """.trimIndent())
        }

        val geminiExecutionFile = File(geminiDir, "execution-rules.md")
        if (!geminiExecutionFile.exists()) {
            geminiExecutionFile.writeText("""
                RULES FOR EXECUTION (CRITICAL, FOLLOW EXACTLY):
                1. You are now in EXECUTION MODE. You must strictly execute the approved plan above.
                2. You MUST use the provided function tools (like `createFile` or `insertCode`) to apply the changes. 
                3. DO NOT EXPLORE. Do NOT call `listDirectory` or `getFileContent` unless explicitly needed.
                4. SPEED: Minimize tool calls. Execute actions immediately.
                5. FILE NAMES: When modifying files, provide ONLY the exact file name.
                6. END CONDITION: Once you have successfully executed the final step in the plan, STOP IMMEDIATELY. Output a brief text summary of what you did.
            """.trimIndent())
        }

        val localExecutionFile = File(localDir, "execution-rules.md")
        if (!localExecutionFile.exists()) {
            localExecutionFile.writeText("""
                CRITICAL INSTRUCTION: You are a programmatic tool-calling system, NOT a conversational chatbot.
                1. You MUST use the provided JSON functions (e.g., `createFile`, `insertCode`) to execute the plan.
                2. DO NOT explain your steps. DO NOT write plain text code. 
                3. PATH RULE: When using `createFile`, you MUST provide the FULL path including the source root.
                4. UNKNOWN PATHS: If you do not know the exact full path, you MUST call `listDirectory` FIRST.
                5. Once ALL steps are successfully completed, reply with exactly: TASK COMPLETE
            """.trimIndent())
        }
    }
}