package se.kodasmart.smartagent.toolWindow.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.ui.CheckboxTree
import com.intellij.ui.CheckedTreeNode
import com.intellij.ui.components.JBScrollPane
import java.awt.BorderLayout
import java.io.File
import javax.swing.BorderFactory
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTree
import javax.swing.SwingUtilities
import javax.swing.tree.DefaultTreeModel

class SourcesPanel(private val project: Project) {
    val component: JPanel
    private val scopeTree: CheckboxTree
    private val rootScopeNode: CheckedTreeNode

    init {
        val (tree, rootNode) = createProjectDirectoryTree(project)
        scopeTree = tree
        rootScopeNode = rootNode

        component = JPanel(BorderLayout()).apply {
            val helpText = JLabel(" Mark which directories the agent should have access to:").apply {
                border = BorderFactory.createEmptyBorder(10, 10, 10, 10)
            }
            add(helpText, BorderLayout.NORTH)
            add(JBScrollPane(scopeTree), BorderLayout.CENTER)
        }

        setupVfsListener()
    }

    fun getSelectedPaths(): List<String> = getSelectedFolderPaths(rootScopeNode, "")

    private fun setupVfsListener() {
        project.messageBus.connect().subscribe(VirtualFileManager.VFS_CHANGES, object : BulkFileListener {
            override fun after(events: List<VFileEvent>) {
                val needsRefresh = events.any { event ->
                    event.file?.isDirectory == true || event is VFileDeleteEvent || event is VFileCreateEvent
                }
                if (needsRefresh) {
                    SwingUtilities.invokeLater { refreshProjectDirectoryTree() }
                }
            }
        })
    }

    private fun createProjectDirectoryTree(project: Project): Pair<CheckboxTree, CheckedTreeNode> {
        val basePath = project.basePath ?: return createEmptyTree()
        val rootDir = File(basePath)
        val rootNode = CheckedTreeNode(rootDir.name)

        populateTree(rootDir, rootNode)

        val renderer = object : CheckboxTree.CheckboxTreeCellRenderer() {
            override fun customizeRenderer(tree: JTree?, value: Any?, selected: Boolean, expanded: Boolean, leaf: Boolean, row: Int, hasFocus: Boolean) {
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

    private fun populateTree(file: File, parentNode: CheckedTreeNode, currentPath: String = "", checkedPaths: Set<String> = emptySet()) {
        val ignoredDirs = setOf(".git", ".gradle", "build", ".idea", "node_modules", "out")
        file.listFiles()?.sortedWith(compareBy({ !it.isDirectory }, { it.name }))?.forEach { child ->
            if (child.isDirectory && child.name !in ignoredDirs) {
                val childNode = CheckedTreeNode(child.name)
                val nodePath = if (currentPath.isEmpty()) child.name else "$currentPath/${child.name}"

                childNode.isChecked = checkedPaths.contains(nodePath)
                parentNode.add(childNode)

                populateTree(child, childNode, nodePath, checkedPaths)
            }
        }
    }

    private fun getSelectedFolderPaths(node: CheckedTreeNode, currentPath: String): List<String> {
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

    private fun refreshProjectDirectoryTree() {
        val basePath = project.basePath ?: return
        val rootDir = File(basePath)
        val currentlyCheckedPaths = getSelectedFolderPaths(rootScopeNode, "").toSet()

        rootScopeNode.removeAllChildren()
        val rootName = rootScopeNode.userObject?.toString() ?: rootDir.name

        populateTree(rootDir, rootScopeNode, rootName, currentlyCheckedPaths)
        (scopeTree.model as DefaultTreeModel).reload()
    }
}