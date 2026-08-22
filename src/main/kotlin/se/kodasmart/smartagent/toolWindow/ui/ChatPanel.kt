package se.kodasmart.smartagent.toolWindow.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.commonmark.parser.Parser
import org.commonmark.renderer.html.HtmlRenderer
import se.kodasmart.smartagent.core.AgentLoop
import se.kodasmart.smartagent.core.GeminiAgentLoop
import se.kodasmart.smartagent.core.LocalAgentLoop
import se.kodasmart.smartagent.llm.GeminiProvider
import se.kodasmart.smartagent.llm.LocalLlmProvider
import se.kodasmart.smartagent.llm.LlmProvider
import se.kodasmart.smartagent.settings.AgentSettings
import se.kodasmart.smartagent.tools.CodeWorkspaceTools
import java.awt.BorderLayout
import java.awt.Font
import java.awt.event.ActionEvent
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import javax.swing.AbstractAction
import javax.swing.BorderFactory
import javax.swing.DefaultComboBoxModel
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTextPane
import javax.swing.KeyStroke
import javax.swing.SwingUtilities
import javax.swing.Timer
import kotlin.coroutines.resume

class ChatPanel(
    private val project: Project,
    private val coroutineScope: CoroutineScope,
    private val sourcesPanel: SourcesPanel
) {
    val component: JPanel

    private val statusLabel = JLabel(" ").apply {
        font = font.deriveFont(Font.ITALIC, 11f)
        foreground = JBColor.GRAY
    }
    private val modelSelector = ComboBox<String>()
    private val chatArea = JTextPane()
    private val inputField = JBTextArea()
    private val sendButton = JButton("Send")

    private val htmlContent = StringBuilder("<html><body style='font-family: sans-serif; font-size: 13px; padding: 10px;'>")
    private val parser = Parser.builder().build()
    private val renderer = HtmlRenderer.builder().build()

    private var activeJob: Job? = null

    init {
        setupUI()
        setupListeners()

        component = JPanel(BorderLayout()).apply {
            val topChatPanel = JPanel(BorderLayout()).apply {
                border = BorderFactory.createEmptyBorder(5, 5, 5, 5)
                add(JLabel("Model: "), BorderLayout.WEST)
                add(modelSelector, BorderLayout.CENTER)
            }

            val inputWrapper = JPanel(BorderLayout()).apply {
                border = BorderFactory.createEmptyBorder(5, 5, 5, 5)
                val buttonPanel = JPanel(java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 0, 0)).apply {
                    border = BorderFactory.createEmptyBorder(5, 0, 0, 0)
                    add(sendButton)
                }
                add(statusLabel, BorderLayout.NORTH)
                add(JBScrollPane(inputField), BorderLayout.CENTER)
                add(buttonPanel, BorderLayout.SOUTH)
            }

            val splitter = com.intellij.ui.JBSplitter(true, 0.8f).apply {
                firstComponent = JBScrollPane(chatArea)
                secondComponent = inputWrapper
                dividerWidth = 3
            }

            add(topChatPanel, BorderLayout.NORTH)
            add(splitter, BorderLayout.CENTER)
        }

        if (AgentSettings.providerType == "LOCAL" || AgentSettings.apiKey.isNotBlank()) {
            loadModels()
        }
    }

    private fun setupUI() {
        chatArea.apply {
            contentType = "text/html"
            isEditable = false
            background = com.intellij.util.ui.UIUtil.getPanelBackground()
        }

        inputField.apply {
            lineWrap = true
            wrapStyleWord = true
            rows = 2
            font = Font("SansSerif", Font.PLAIN, 13)
            border = BorderFactory.createEmptyBorder(5, 5, 5, 5)
        }
    }

    private fun setupListeners() {
        sendButton.addActionListener { handleSendAction() }

        val enterStroke = KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0)
        inputField.inputMap.put(enterStroke, "SEND_MESSAGE")
        inputField.actionMap.put("SEND_MESSAGE", object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent?) {
                if (inputField.isEnabled) {
                    handleSendAction()
                }
            }
        })

        val shiftEnterStroke = KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.SHIFT_DOWN_MASK)
        inputField.inputMap.put(shiftEnterStroke, "insert-break")
    }

    private fun getActiveProvider(): LlmProvider {
        return if (AgentSettings.providerType == "LOCAL") {
            LocalLlmProvider(AgentSettings.localLlmUrl)
        } else {
            GeminiProvider(AgentSettings.apiKey)
        }
    }

    fun loadModels() {
        coroutineScope.launch {
            try {
                SwingUtilities.invokeLater { statusLabel.text = "Loading Models..." }

                val isLocal = AgentSettings.providerType == "LOCAL"
                if (!isLocal && AgentSettings.apiKey.isBlank()) {
                    SwingUtilities.invokeLater { statusLabel.text = "No API-key configured." }
                    return@launch
                }
                if (isLocal && AgentSettings.localLlmUrl.isBlank()) {
                    SwingUtilities.invokeLater { statusLabel.text = "No Local LLM URL configured." }
                    return@launch
                }

                val models = getActiveProvider().getAvailableModels()

                SwingUtilities.invokeLater {
                    modelSelector.model = DefaultComboBoxModel(models.toTypedArray())
                    if (models.contains("gemini-3.7-flash")) {
                        modelSelector.selectedItem = "gemini-3.7-flash"
                    } else if (models.contains("gemini-3.6-flash")) {
                        modelSelector.selectedItem = "gemini-3.6-flash"
                    } else if (models.isNotEmpty()) {
                        modelSelector.selectedIndex = 0
                    }
                    statusLabel.text = "Loaded the models!"

                    Timer(3000) { statusLabel.text = " " }.apply {
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

    private fun appendMessage(sender: String, message: String, isUser: Boolean) {
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

    private fun handleSendAction() {
        val userText = inputField.text
        val selectedModel = modelSelector.selectedItem as? String

        if (selectedModel == null) {
            SwingUtilities.invokeLater {
                statusLabel.text = "Error: No model selected."
                appendMessage("System Error", "Ingen modell vald. Kontrollera anslutningen.", false)
            }
            return
        }

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

        val relevantPaths = sourcesPanel.getSelectedPaths()

        activeJob = coroutineScope.launch {
            runAgentLoop(userText, selectedModel, relevantPaths)
        }
    }

    private suspend fun runAgentLoop(userText: String, selectedModel: String, relevantPaths: List<String>) {
        try {
            val isLocal = AgentSettings.providerType == "LOCAL"
            if (!isLocal && AgentSettings.apiKey.isBlank()) {
                SwingUtilities.invokeLater { appendMessage("System Error", "API Key saknas.", false) }
                return
            }
            if (isLocal && AgentSettings.localLlmUrl.isBlank()) {
                SwingUtilities.invokeLater { appendMessage("System Error", "Lokal LLM URL saknas.", false) }
                return
            }

            val activeProvider = getActiveProvider()
            val tools = CodeWorkspaceTools(project)

            val agentLoop = if (isLocal) {
                LocalAgentLoop(tools, activeProvider)
            } else {
                GeminiAgentLoop(tools, activeProvider)
            }

            agentLoop.runLoop(
                userMessage = userText,
                modelName = selectedModel,
                relevantPaths = relevantPaths,
                maxIterations = AgentSettings.maxIterations,
                onStatusUpdate = { status -> SwingUtilities.invokeLater { statusLabel.text = status } },
                onToolAction = { actionLog -> SwingUtilities.invokeLater { appendMessage("System Log", actionLog, false) } },
                onPlanGenerated = { generatedPlan -> showPlanDialog(generatedPlan) }
            )

            SwingUtilities.invokeLater { statusLabel.text = "Klar." }
        } catch (e: CancellationException) {
            SwingUtilities.invokeLater { statusLabel.text = "Avbruten." }
        } catch (e: Exception) {
            SwingUtilities.invokeLater { appendMessage("Error", e.message ?: "Okänt fel", false) }
        } finally {
            SwingUtilities.invokeLater {
                sendButton.text = "Send"
                inputField.isEnabled = true
                inputField.requestFocus()
            }
        }
    }

    private suspend fun showPlanDialog(generatedPlan: String): String? = suspendCancellableCoroutine { continuation ->
        com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater({
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
                override fun createCenterPanel(): javax.swing.JComponent = JBScrollPane(textArea).apply {
                    preferredSize = java.awt.Dimension(600, 400)
                }
            }
            if (dialog.showAndGet()) continuation.resume(dialog.textArea.text)
            else continuation.resume(null)
        }, com.intellij.openapi.application.ModalityState.defaultModalityState())
    }
}