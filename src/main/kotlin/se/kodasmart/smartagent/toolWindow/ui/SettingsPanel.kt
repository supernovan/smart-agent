package se.kodasmart.smartagent.toolWindow.ui

import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBPasswordField
import com.intellij.ui.components.JBTextField
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import se.kodasmart.smartagent.settings.AgentSettings
import java.awt.BorderLayout
import java.awt.Component
import java.awt.FlowLayout
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JSpinner
import javax.swing.SpinnerNumberModel
import javax.swing.SwingUtilities
import javax.swing.Timer

class SettingsPanel(
    private val coroutineScope: CoroutineScope,
    private val onSettingsSaved: () -> Unit
) {
    val component: JPanel

    private val providerSelector = ComboBox(arrayOf("GEMINI", "LOCAL"))
    private val apiKeyField = JBPasswordField().apply { columns = 25 }
    private val localUrlField = JBTextField().apply { columns = 25 }
    private val iterationsSpinner = JSpinner(SpinnerNumberModel(1, 1, 100, 1))
    private val saveButton = JButton("Save Settings")
    private val saveStatusLabel = JLabel(" ").apply { foreground = JBColor.LIGHT_GRAY }
    private val apiKeyPanel = JPanel(BorderLayout())
    private val localUrlPanel = JPanel(BorderLayout())

    init {
        providerSelector.selectedItem = AgentSettings.providerType
        localUrlField.text = AgentSettings.localLlmUrl
        iterationsSpinner.value = AgentSettings.maxIterations

        coroutineScope.launch {
            val savedKey = AgentSettings.apiKey
            SwingUtilities.invokeLater { apiKeyField.text = savedKey }
        }

        setupPanels()
        setupListeners()

        val settingsFormPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = BorderFactory.createEmptyBorder(10, 10, 10, 10)

            val providerPanel = JPanel(BorderLayout()).apply {
                add(JLabel("Provider:"), BorderLayout.NORTH)
                add(providerSelector, BorderLayout.CENTER)
                alignmentX = Component.LEFT_ALIGNMENT
            }

            add(providerPanel)
            add(Box.createVerticalStrut(10))
            add(apiKeyPanel)
            add(localUrlPanel)
            add(Box.createVerticalStrut(15))

            val iterPanel = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
                add(JLabel("Max Tool Iterations: "))
                add(iterationsSpinner)
                alignmentX = Component.LEFT_ALIGNMENT
            }
            add(iterPanel)
            add(Box.createVerticalStrut(15))

            val actionPanel = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
                add(saveButton)
                add(Box.createHorizontalStrut(10))
                add(saveStatusLabel)
                alignmentX = Component.LEFT_ALIGNMENT
            }
            add(actionPanel)
        }

        component = JPanel(BorderLayout()).apply {
            add(settingsFormPanel, BorderLayout.NORTH)
        }
    }

    private fun setupPanels() {
        apiKeyPanel.apply {
            add(JLabel("Gemini API Key:"), BorderLayout.NORTH)
            add(apiKeyField, BorderLayout.CENTER)
            val note = JLabel("<html><small>Your key is stored securely in IDE properties.</small></html>").apply {
                foreground = JBColor.GRAY
            }
            add(note, BorderLayout.SOUTH)
            alignmentX = Component.LEFT_ALIGNMENT
        }

        localUrlPanel.apply {
            add(JLabel("Local LLM Base URL (e.g. http://localhost:11434/v1):"), BorderLayout.NORTH)
            add(localUrlField, BorderLayout.CENTER)
            alignmentX = Component.LEFT_ALIGNMENT
        }

        updateProviderVisibility(AgentSettings.providerType)
    }

    private fun setupListeners() {
        providerSelector.addActionListener {
            val selected = providerSelector.selectedItem as? String ?: "GEMINI"
            updateProviderVisibility(selected)
        }

        saveButton.addActionListener {
            saveSettings()
        }
    }

    private fun updateProviderVisibility(selectedProvider: String) {
        val isLocal = selectedProvider == "LOCAL"
        apiKeyPanel.isVisible = !isLocal
        localUrlPanel.isVisible = isLocal
    }

    private fun saveSettings() {
        val newProvider = providerSelector.selectedItem as String
        val newKey = String(apiKeyField.password).trim()
        val newUrl = localUrlField.text.trim()
        val newIterations = iterationsSpinner.value as Int

        saveButton.isEnabled = false

        coroutineScope.launch {
            AgentSettings.providerType = newProvider
            AgentSettings.apiKey = newKey
            AgentSettings.localLlmUrl = newUrl
            AgentSettings.maxIterations = newIterations

            SwingUtilities.invokeLater {
                saveStatusLabel.text = "Saved successfully!"
                saveButton.isEnabled = true
                onSettingsSaved()

                Timer(3000) { saveStatusLabel.text = " " }.apply {
                    isRepeats = false
                    start()
                }
            }
        }
    }
}