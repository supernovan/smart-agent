# Smart Agent

**Smart Agent** is an autonomous AI coding assistant integrated directly into IntelliJ IDEA. It goes beyond simple code completion by leveraging an agentic loop—planning, executing, and verifying tasks—while strictly enforcing your project's architectural rules and coding standards.

## Features

- **Autonomous Agentic Loop:** Analyzes tasks, creates execution plans, and executes them step-by-step.
- **Workspace Awareness:** Understands your project structure, file dependencies, and context.
- **Architectural Guardrails:** Uses local configuration (`.gemini-agent/`) to enforce your specific coding standards (e.g., DDD, naming conventions, testing requirements).
- **Customizable:** Configure iteration limits, custom rules, and API keys per project.

## 🛠 Getting Started

1. **Installation:** Build the plugin using `./gradlew buildPlugin` and install the zip from `build/distributions/`.
2. **Configuration:**
    - Open the **Gemini Agent** tool window (default: Right sidebar).
    - Go to the **Settings** tab.
    - Enter your Gemini API Key and set your desired iteration limit.
3. **Usage:**
    - Select relevant files/folders in the **Sources** tab.
    - Enter your task in the **Chat** tab.
    - Review the generated plan, approve it, and watch the agent work.

## Architecture

The agent operates on a `Planning` -> `Execution` loop. It utilizes a custom convention system to ensure that code generated adheres to the `Code Smart AB` standard.

## 🗺 Roadmap

- [x] Gemini API Integration
- [ ] **Support for Local LLMs (Qwen/Llama via Ollama)**
- [ ] Advanced Context Caching
- [ ] Multi-file editing workflow improvements

---
*Built by Code Smart AB.*