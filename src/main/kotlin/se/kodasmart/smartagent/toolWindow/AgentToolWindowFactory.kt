package se.kodasmart.smartagent.toolWindow

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.CheckboxTree
import com.intellij.ui.CheckedTreeNode
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import com.intellij.ui.content.ContentFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.commonmark.parser.Parser
import org.commonmark.renderer.html.HtmlRenderer
import se.kodasmart.smartagent.api.client.GeminiApiClient
import se.kodasmart.smartagent.core.AgentLoop
import se.kodasmart.smartagent.llm.GeminiProvider
import se.kodasmart.smartagent.settings.AgentSettings
import se.kodasmart.smartagent.tools.CodeWorkspaceTools
import java.awt.BorderLayout
import java.awt.Font
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.io.File
import javax.swing.DefaultComboBoxModel
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTextPane
import javax.swing.JTree
import javax.swing.SwingUtilities
import kotlin.coroutines.resume

class AgentToolWindowFactory : ToolWindowFactory {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        toolWindow.stripeTitle = "Gemini Agent"

        val statusLabel = JLabel(" ").apply {
            font = font.deriveFont(Font.ITALIC, 11f)
            foreground = JBColor.GRAY
        }



        // ==========================================
        // 2: SCOPE TREE
        // ==========================================
        val (scopeTree, rootScopeNode) = createProjectDirectoryTree(project)
        val treeScrollPane = JBScrollPane(scopeTree)

        val sourcesPanel = JPanel(BorderLayout()).apply {
            val helpText = JLabel(" Markera de mappar agenten ska ha tillgång till:").apply {
                border = javax.swing.BorderFactory.createEmptyBorder(10, 10, 10, 10)
            }
            add(helpText, BorderLayout.NORTH)
            add(treeScrollPane, BorderLayout.CENTER)
        }


        // ==========================================
        // 1: CHAT AND INPUT
        // ==========================================
        val modelSelector = ComboBox<String>()
        val topChatPanel = JPanel(BorderLayout()).apply {
            border = javax.swing.BorderFactory.createEmptyBorder(5, 5, 5, 5)
            add(JLabel("Model: "), BorderLayout.WEST)
            add(modelSelector, BorderLayout.CENTER)
        }

        val chatArea = JTextPane().apply {
            contentType = "text/html"
            isEditable = false
            background = com.intellij.util.ui.UIUtil.getPanelBackground()
        }

        val htmlContent = java.lang.StringBuilder("<html><body style='font-family: sans-serif; font-size: 13px; padding: 10px;'>")
        val parser = Parser.builder().build()
        val renderer = HtmlRenderer.builder().build()

        fun appendMessage(sender: String, message: String, isUser: Boolean) {
            val document = parser.parse(message)
            val parsedHtml = renderer.render(document)
            val styledHtml = parsedHtml
                .replace("<pre>", "<pre style='background-color: #2B2B2B; color: #A9B7C6; padding: 8px; font-family: monospace; font-size: 11px;'>")
                .replace("<code>", "<code style='font-family: monospace; font-size: 11px; background-color: #2B2B2B; color: #A9B7C6;'>")

            val icon = if (isUser) "👤 You" else "✨ Agent"
            htmlContent.append("""
                <div style='margin-bottom: 12px;'>
                    <b>$icon</b><br/>
                    $styledHtml
                </div>
                <hr style='border: none; border-top: 1px solid #555555; margin: 8px 0;'/>
            """.trimIndent())

            chatArea.text = "$htmlContent</body></html>"
            chatArea.caretPosition = chatArea.document.length
        }

        val inputField = JBTextField()
        val sendButton = JButton("Send")

        val inputWrapper = JPanel(BorderLayout()).apply {
            border = javax.swing.BorderFactory.createEmptyBorder(5, 5, 5, 5)
            add(statusLabel, BorderLayout.NORTH)
            val fieldAndButtonPanel = JPanel(BorderLayout()).apply {
                add(inputField, BorderLayout.CENTER)
                add(sendButton, BorderLayout.EAST)
            }
            add(fieldAndButtonPanel, BorderLayout.CENTER)
        }

        val chatPanel = JPanel(BorderLayout()).apply {
            add(topChatPanel, BorderLayout.NORTH)
            add(JBScrollPane(chatArea), BorderLayout.CENTER)
            add(inputWrapper, BorderLayout.SOUTH)
        }

        // ==========================================
        // API AND LOGIK
        // ==========================================
        val currentApiKey = AgentSettings.apiKey
        val provider = GeminiProvider(apiKey = currentApiKey)

        val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

        fun loadModels() {
            coroutineScope.launch {
                try {
                    SwingUtilities.invokeLater { statusLabel.text = "Loading Models..." }

                    val currentKey = AgentSettings.apiKey
                    if (currentKey.isBlank()) {
                        SwingUtilities.invokeLater { statusLabel.text = "No api-key could be found." }
                        return@launch
                    }

                    val provider = GeminiProvider(currentApiKey)
                    val models = provider.getAvailableModels()

                    SwingUtilities.invokeLater {
                        modelSelector.model = DefaultComboBoxModel(models.toTypedArray())
                        if (models.contains("gemini-3.7-flash")) {
                            modelSelector.selectedItem = "gemini-3.7-flash"
                        }
                        statusLabel.text = "Loaded the models!"

                        javax.swing.Timer(3000) { statusLabel.text = " " }.apply {
                            isRepeats = false
                            start()
                        }
                    }
                } catch (e: Exception) {
                    SwingUtilities.invokeLater {
                        statusLabel.text = "Failed to fetch models."
                        appendMessage("System", "Could not load models:\n ${e.message}", false)
                    }
                }
            }
        }

        if (AgentSettings.apiKey.isNotBlank()) {
            loadModels()
        }

        // ==========================================
        // 3: SETTINGS
        // ==========================================

        val apiKeyField = com.intellij.ui.components.JBPasswordField().apply {
            text = AgentSettings.apiKey
            columns = 25
        }

        val spinnerModel = javax.swing.SpinnerNumberModel(AgentSettings.maxIterations, 1, 100, 1)
        val iterationsSpinner = javax.swing.JSpinner(spinnerModel)

        val saveButton = JButton("Save Settings")
        val saveStatusLabel = JLabel(" ").apply {
            foreground = JBColor.LIGHT_GRAY
        }

        saveButton.addActionListener {
            AgentSettings.apiKey = String(apiKeyField.password).trim()
            AgentSettings.maxIterations = iterationsSpinner.value as Int
            saveStatusLabel.text = "Saved successfully!"

            loadModels()

            javax.swing.Timer(3000) { saveStatusLabel.text = " " }.apply {
                isRepeats = false
                start()
            }
        }

        val settingsFormPanel = JPanel().apply {
            layout = javax.swing.BoxLayout(this, javax.swing.BoxLayout.Y_AXIS)
            border = javax.swing.BorderFactory.createEmptyBorder(10, 10, 10, 10)

            val apiKeyPanel = JPanel(BorderLayout()).apply {
                add(JLabel("Gemini API Key:"), BorderLayout.NORTH)
                add(apiKeyField, BorderLayout.CENTER)
                val note = JLabel("<html><small>Your key is stored securely in IDE properties.</small></html>").apply {
                    foreground = JBColor.GRAY
                }
                add(note, BorderLayout.SOUTH)
            }
            apiKeyPanel.alignmentX = java.awt.Component.LEFT_ALIGNMENT
            add(apiKeyPanel)

            add(javax.swing.Box.createVerticalStrut(15))

            // Iterationer sektion
            val iterPanel = JPanel(java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 0, 0)).apply {
                add(JLabel("Max Tool Iterations: "))
                add(iterationsSpinner)
            }
            iterPanel.alignmentX = java.awt.Component.LEFT_ALIGNMENT
            add(iterPanel)

            add(javax.swing.Box.createVerticalStrut(15))

            val actionPanel = JPanel(java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 0, 0)).apply {
                add(saveButton)
                add(javax.swing.Box.createHorizontalStrut(10))
                add(saveStatusLabel)
            }
            actionPanel.alignmentX = java.awt.Component.LEFT_ALIGNMENT
            add(actionPanel)
        }

        val settingsPanel = JPanel(BorderLayout()).apply {
            add(settingsFormPanel, BorderLayout.NORTH)
        }

        // ==========================================
        // COMBINE
        // ==========================================
        val tabbedPane = com.intellij.ui.components.JBTabbedPane().apply {
            addTab("Chat", chatPanel)
            addTab("Sources", sourcesPanel)
            addTab("Settings", settingsPanel)
        }

        val mainPanel = JPanel(BorderLayout()).apply {
            add(tabbedPane, BorderLayout.CENTER)
        }




        var activeJob: Job? = null

        fun sendMessage() {
            val userText = inputField.text
            val selectedModel = modelSelector.selectedItem as? String ?: return

            val relevantPaths = getSelectedFolderPaths(rootScopeNode)

            if (activeJob?.isActive == true) {
                activeJob?.cancel()
                statusLabel.text = "Avbruten."
                sendButton.text = "Send"
                inputField.isEnabled = true
                return
            }

            if (userText.isBlank()) return

            appendMessage("You", userText, true)
            inputField.text = ""
            inputField.isEnabled = false
            sendButton.text = "Cancel"

            activeJob = coroutineScope.launch {
                try {
                    val currentApiKey = AgentSettings.apiKey
                    if (currentApiKey.isBlank()) {
                        SwingUtilities.invokeLater {
                            appendMessage("System Error", "API Key is missing. Please configure it in the Settings tab.", false)
                        }
                        return@launch
                    }

                    val dynamicApiClient = GeminiApiClient(currentApiKey)
                    val tools = CodeWorkspaceTools(project)
                    val agentLoop = AgentLoop(tools, provider)

                    agentLoop.runLoop(
                        userMessage = userText,
                        modelName = selectedModel,
                        relevantPaths = relevantPaths,
                        maxIterations = AgentSettings.maxIterations,
                        onStatusUpdate = { status ->
                            SwingUtilities.invokeLater { statusLabel.text = status }
                        },
                        onToolAction = { actionLog ->
                            SwingUtilities.invokeLater { appendMessage("System Log", actionLog, false) }
                        },
                        onPlanGenerated = { generatedPlan ->
                            suspendCancellableCoroutine { continuation ->
                                SwingUtilities.invokeLater {
                                    val dialog = object : com.intellij.openapi.ui.DialogWrapper(project, true) {
                                        val textArea = javax.swing.JTextArea(generatedPlan).apply {
                                            lineWrap = true
                                            wrapStyleWord = true
                                            font = Font("Monospaced", Font.PLAIN, 13)
                                        }
                                        init {
                                            title = "View and edit the plan"
                                            setOKButtonText("Approve & execute")
                                            setCancelButtonText("Cancel")
                                            init()
                                        }
                                        override fun createCenterPanel(): javax.swing.JComponent {
                                            return JBScrollPane(textArea).apply {
                                                preferredSize = java.awt.Dimension(600, 400)
                                            }
                                        }
                                    }
                                    if (dialog.showAndGet()) continuation.resume(dialog.textArea.text)
                                    else continuation.resume(null)
                                }
                            }
                        }
                    )
                    SwingUtilities.invokeLater { statusLabel.text = "Done." }
                } catch (e: CancellationException) {
                    SwingUtilities.invokeLater { statusLabel.text = "Canceled." }
                } catch (e: Exception) {
                    SwingUtilities.invokeLater { appendMessage("Error", e.message ?: "Unknown", false) }
                } finally {
                    SwingUtilities.invokeLater {
                        sendButton.text = "Send"
                        inputField.isEnabled = true
                        inputField.requestFocus()
                    }
                }
            }
        }

        sendButton.addActionListener { sendMessage() }
        inputField.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                if (e.keyCode == KeyEvent.VK_ENTER) sendMessage()
            }
        })

        val contentFactory = ContentFactory.getInstance()
        val content = contentFactory.createContent(mainPanel, "", false)
        toolWindow.contentManager.addContent(content)
    }

    fun createProjectDirectoryTree(project: Project): Pair<CheckboxTree, CheckedTreeNode> {
        val basePath = project.basePath ?: return createEmptyTree()
        val rootDir = File(basePath)
        val rootNode = CheckedTreeNode(rootDir.name)

        fun populateTree(file: File, parentNode: CheckedTreeNode) {
            val ignoredDirs = setOf(".git", ".gradle", "build", ".idea", "node_modules", "out")
            file.listFiles()?.sortedWith(compareBy({ !it.isDirectory }, { it.name }))?.forEach { child ->
                if (child.isDirectory && child.name !in ignoredDirs) {
                    val childNode = CheckedTreeNode(child.name)
                    childNode.isChecked = false
                    parentNode.add(childNode)
                    populateTree(child, childNode)
                }
            }
        }

        populateTree(rootDir, rootNode)

        val renderer = object : CheckboxTree.CheckboxTreeCellRenderer() {
            override fun customizeRenderer(
                tree: JTree?,
                value: Any?,
                selected: Boolean,
                expanded: Boolean,
                leaf: Boolean,
                row: Int,
                hasFocus: Boolean
            ) {
                if (value is CheckedTreeNode) {
                    textRenderer.append(value.userObject?.toString() ?: "")
                }
            }
        }

        val tree = CheckboxTree(renderer, rootNode)
        tree.setRootVisible(false)
        return Pair(tree, rootNode)
    }

    private fun createEmptyTree(): Pair<CheckboxTree, CheckedTreeNode> {
        val root = CheckedTreeNode("Root")
        val renderer = object : CheckboxTree.CheckboxTreeCellRenderer() {
            override fun customizeRenderer(t: JTree?, v: Any?, s: Boolean, e: Boolean, l: Boolean, r: Int, h: Boolean) {}
        }
        return Pair(CheckboxTree(renderer, root), root)
    }

    fun getSelectedFolderPaths(node: CheckedTreeNode, currentPath: String = ""): List<String> {
        val results = mutableListOf<String>()
        val nodeName = node.userObject?.toString() ?: return results
        val path = if (currentPath.isEmpty()) nodeName else "$currentPath/$nodeName"

        if (node.isChecked) {
            results.add(path)
        }

        for (i in 0 until node.childCount) {
            val child = node.getChildAt(i)
            if (child is CheckedTreeNode) {
                results.addAll(getSelectedFolderPaths(child, path))
            }
        }
        return results
    }
}