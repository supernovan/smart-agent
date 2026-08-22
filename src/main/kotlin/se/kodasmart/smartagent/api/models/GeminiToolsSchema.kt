package se.kodasmart.smartagent.api.models

object GeminiToolsSchema {

    val availableTools = listOf(
        FunctionDeclaration(
            name = "getFileContent",
            description = "Retrieves the full text content of a specific file in the project.",
            parameters = Schema(
                type = "OBJECT",
                properties = mapOf(
                    "fileName" to SchemaProperty("STRING", "The exact name of the file, e.g., 'Application.kt'")
                ),
                required = listOf("fileName")
            )
        ),
        FunctionDeclaration(
            name = "insertCode",
            description = "Inserts new code at a specific offset in a file.",
            parameters = Schema(
                type = "OBJECT",
                properties = mapOf(
                    "fileName" to SchemaProperty("STRING", "The name of the file."),
                    "offset" to SchemaProperty("INTEGER", "The character index where the code should be inserted."),
                    "newCode" to SchemaProperty("STRING", "The new code to insert.")
                ),
                required = listOf("fileName", "offset", "newCode")
            )
        ),
        FunctionDeclaration(
            name = "replaceCodeBlock",
            description = "Replaces a specific block of code in a file with new code. You must provide the EXACT existing code block you want to replace in the searchString parameter.",
            parameters = Schema(
                type = "OBJECT",
                properties = mapOf(
                    "fileName" to SchemaProperty("STRING", "The name of the file."),
                    "searchString" to SchemaProperty("STRING", "The EXACT code currently in the file that you want to replace, including all whitespace."),
                    "replacementString" to SchemaProperty("STRING", "The new code that will replace the searchString.")
                ),
                required = listOf("fileName", "searchString", "replacementString")
            )
        ),
        FunctionDeclaration(
            name = "createFile",
            description = "Creates a new file in the project with the specified content.",
            parameters = Schema(
                type = "OBJECT",
                properties = mapOf(
                    "relativePath" to SchemaProperty("STRING", "The path where the file should be created, relative to the project root (e.g., 'src/main/kotlin/se/kodasmart/MyClass.kt')."),
                    "content" to SchemaProperty("STRING", "The full source code to write to the new file.")
                ),
                required = listOf("relativePath", "content")
            )
        ),
        FunctionDeclaration(
            name = "listDirectory",
            description = "Lists all files in a specific directory so you can see which files exist.",
            parameters = Schema(
                type = "OBJECT",
                properties = mapOf(
                    "directoryPath" to SchemaProperty("STRING", "The path of the directory to list (e.g., 'se/booksmart/bookandstuff/salary/models').")
                ),
                required = listOf("directoryPath")
            )
        ),
    )
}