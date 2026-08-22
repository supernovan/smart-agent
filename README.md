# Smart Agent

**Smart Agent** Is a fun project trying to create an agentic plugin for INtellij, which probably already exist and is better.


## Features

- **Autonomous Agentic Loop:** Analyzes tasks, creates execution plans, and executes them step-by-step.
- **Architectural Guardrails:** Uses local configuration (`.smart-agent/`) to enforce your specific coding standards (e.g., DDD, naming conventions, testing requirements).

## Getting Started

1. **Installation:** Build the plugin using `./gradlew buildPlugin` and install the zip from `build/distributions/`.
2. **Configuration:**
    - Open the **Smart Agent** tool window (default: Right sidebar).
    - Go to the **Settings** tab.
    - Enter your Gemini API Key and set your desired iteration limit.
3. **Usage:**
    - Select relevant files/folders in the **Sources** tab.
    - Enter your task in the **Chat** tab.
    - Review the generated plan, approve it, and watch the agent work.

## Architecture

The agent operates on a `Planning` -> `Execution` loop.

---
*Built by Code Smart AB.*