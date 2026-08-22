package se.kodasmart.smartagent.settings

import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.generateServiceName
import com.intellij.ide.passwordSafe.PasswordSafe
import com.intellij.ide.util.PropertiesComponent

object AgentSettings {

    private val CREDENTIAL_ATTRIBUTES = CredentialAttributes(
        generateServiceName("GeminiSmartAgent", "ApiKey")
    )

    var apiKey: String
        get() {
            return PasswordSafe.instance.getPassword(CREDENTIAL_ATTRIBUTES) ?: ""
        }
        set(value) {
            if (value.isBlank()) {
                PasswordSafe.instance.set(CREDENTIAL_ATTRIBUTES, null)
            } else {
                PasswordSafe.instance.setPassword(CREDENTIAL_ATTRIBUTES, value)
            }
        }

    var maxIterations: Int
        get() {
            return PropertiesComponent.getInstance().getInt("se.kodasmart.smartagent.maxIterations", 15)
        }
        set(value) {
            PropertiesComponent.getInstance().setValue("se.kodasmart.smartagent.maxIterations", value, 15)
        }

    var providerType: String
        get() {
            return PropertiesComponent.getInstance().getValue("se.kodasmart.smartagent.providerType", "GEMINI")
        }
        set(value) {
            PropertiesComponent.getInstance().setValue("se.kodasmart.smartagent.providerType", value)
        }

    var localLlmUrl: String
        get() {
            return PropertiesComponent.getInstance().getValue("se.kodasmart.smartagent.localLlmUrl", "http://localhost:11434/v1")
        }
        set(value) {
            PropertiesComponent.getInstance().setValue("se.kodasmart.smartagent.localLlmUrl", value)
        }

    var timeoutSeconds: Int
        get() = PropertiesComponent.getInstance().getInt("se.kodasmart.smartagent.timeoutSeconds", 120)
        set(value) = PropertiesComponent.getInstance().setValue("se.kodasmart.smartagent.timeoutSeconds", value, 120)
}