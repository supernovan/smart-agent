package se.kodasmart.smartagent.settings

import com.intellij.ide.util.PropertiesComponent

object AgentSettings {
    private const val KEY_API_KEY = "se.kodasmart.smartagent.apikey"
    private const val KEY_MAX_ITERATIONS = "se.kodasmart.smartagent.maxiterations"

    var apiKey: String
        get() = PropertiesComponent.getInstance().getValue(KEY_API_KEY, "")
        set(value) = PropertiesComponent.getInstance().setValue(KEY_API_KEY, value)

    var maxIterations: Int
        get() = PropertiesComponent.getInstance().getInt(KEY_MAX_ITERATIONS, 30)
        set(value) = PropertiesComponent.getInstance().setValue(KEY_MAX_ITERATIONS, value.toString())
}