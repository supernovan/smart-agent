package se.kodasmart.smartagent.tools

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import java.io.File

class CodeWorkspaceTools(private val project: Project) {

    private fun findVirtualFile(fileName: String): VirtualFile? {
        return ReadAction.compute<VirtualFile?, Throwable> {
            val scope = GlobalSearchScope.projectScope(project)
            val files = FilenameIndex.getVirtualFilesByName(fileName, scope)
            files.firstOrNull()
        }
    }

    fun getFileContent(fileName: String): String {
        return ReadAction.compute<String, Throwable> {
            val file = findVirtualFile(fileName) ?: return@compute "Error: File '$fileName' not found in project."
            val document = FileDocumentManager.getInstance().getDocument(file)
            document?.text ?: "Error: Could not read file content."
        }
    }

    fun insertCode(fileName: String, offset: Int, newCode: String): String {
        val file = findVirtualFile(fileName) ?: return "Error: File '$fileName' not found."

        return WriteCommandAction.writeCommandAction(project).compute<String, Throwable> {
            val document = FileDocumentManager.getInstance().getDocument(file)
                ?: return@compute "Error: Could not load document for '$fileName'."

            if (offset < 0 || offset > document.textLength) {
                return@compute "Error: Invalid offset $offset. File length is ${document.textLength}."
            }

            document.insertString(offset, newCode)
            "Success: Code inserted in $fileName."
        }
    }

    fun replaceCodeBlock(fileName: String, searchString: String, replacementString: String): String {
        val file = findVirtualFile(fileName) ?: return "Error: File '$fileName' not found."

        return WriteCommandAction.writeCommandAction(project).compute<String, Throwable> {
            val document = FileDocumentManager.getInstance().getDocument(file)
                ?: return@compute "Error: Could not load document for '$fileName'."

            val fullText = document.text
            val startOffset = fullText.indexOf(searchString)

            if (startOffset == -1) {
                return@compute "Error: The exact searchString was not found in the file."
            }

            val endOffset = startOffset + searchString.length
            document.replaceString(startOffset, endOffset, replacementString)

            "Success: Code block replaced in $fileName."
        }
    }

    fun createFile(relativePath: String, content: String): String {
        val basePath = project.basePath ?: return "Error: Project base path not found."
        val file = File(basePath, relativePath)

        if (file.exists()) {
            return "Error: File '$relativePath' already exists."
        }

        return try {
            file.parentFile.mkdirs()
            file.writeText(content)

            ApplicationManager.getApplication().invokeLater {
                LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file)
            }

            "Success: Created file $relativePath"
        } catch (e: Exception) {
            "Error creating file: ${e.message}"
        }
    }

    fun listDirectory(directoryPath: String): String {
        val basePath = project.basePath ?: return "Error: Project base path not found."

        var cleanPath = directoryPath.trimStart('/')

        if (cleanPath.contains('.') && !cleanPath.contains('/')) {
            cleanPath = cleanPath.replace('.', '/')
        }

        var targetFile = File(basePath, cleanPath)
        if (!targetFile.exists() || !targetFile.isDirectory) {
            targetFile = File(basePath, "src/main/kotlin/$cleanPath")
        }

        if (!targetFile.exists() || !targetFile.isDirectory) {
            return "Error: Directory '$directoryPath' not found."
        }

        val fileList = targetFile.listFiles()?.joinToString("\n") { f ->
            if (f.isDirectory) "[DIR] ${f.name}" else f.name
        } ?: "Directory is empty."

        return "Contents of $directoryPath:\n$fileList"
    }

    fun generateRepoMap(relevantSubPaths: List<String> = emptyList()): String {
        val basePath = project.basePath ?: return "Error: Base path not found."
        val rootDir = File(basePath)
        val sb = StringBuilder()

        val pathsToScan = relevantSubPaths.ifEmpty {
            listOf("src/main/kotlin", "src/test/kotlin")
        }

        fun walk(file: File, indent: String) {
            val ignoredDirs = setOf(".git", ".gradle", "build", ".idea", "node_modules", "out", "resources")
            if (file.name in ignoredDirs) return

            val relativePath = file.toRelativeString(rootDir).replace('\\', '/')

            val isRelevant = pathsToScan.any { path ->
                relativePath.startsWith(path) || path.startsWith(relativePath)
            }
            if (!isRelevant) return

            sb.append("$indent- ${file.name}${if (file.isDirectory) "/" else ""}\n")

            if (file.isDirectory) {
                file.listFiles()
                    ?.sortedWith(compareBy({ !it.isDirectory }, { it.name }))
                    ?.forEach { walk(it, "$indent  ") }
            }
        }

        walk(rootDir, "")
        return sb.toString()
    }

    fun getBasePath(): String? {
        return project.basePath
    }
}