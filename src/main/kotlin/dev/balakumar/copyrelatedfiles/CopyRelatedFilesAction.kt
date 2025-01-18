package com.example

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

class CopyRelatedFilesAction : AnAction() {
    private val notificationGroup by lazy {
        NotificationGroupManager.getInstance().getNotificationGroup("Copy Related Files")
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE)

        if (project == null || file == null) {
            notify(project, "Error: No file selected", NotificationType.ERROR)
            return
        }

        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Copying Files", true) {
            override fun run(indicator: ProgressIndicator) {
                try {
                    val clipboardContent = StringBuilder()
                    val processedFiles = mutableSetOf<VirtualFile>()
                    val moduleRoot = findGoModuleRoot(file)

                    if (moduleRoot == null) {
                        notify(project, "No go.mod file found", NotificationType.WARNING)
                        return
                    }

                    // Process the selected file first
                    processFile(file, clipboardContent)
                    processedFiles.add(file)

                    // Find all Go files in the same directory
                    file.parent?.children?.forEach { siblingFile ->
                        if (isGoFile(siblingFile) && !processedFiles.contains(siblingFile)) {
                            processFile(siblingFile, clipboardContent)
                            processedFiles.add(siblingFile)
                        }
                    }

                    if (clipboardContent.isNotEmpty()) {
                        com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater {
                            try {
                                val stringSelection = StringSelection(clipboardContent.toString())
                                val clipboard = Toolkit.getDefaultToolkit().systemClipboard
                                clipboard.setContents(stringSelection, null)
                                notify(project, "Copied ${processedFiles.size} files to clipboard", NotificationType.INFORMATION)
                            } catch (e: Exception) {
                                notify(project, "Error copying to clipboard: ${e.message}", NotificationType.ERROR)
                            }
                        }
                    } else {
                        notify(project, "No content to copy", NotificationType.WARNING)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    notify(project, "Error: ${e.message}", NotificationType.ERROR)
                }
            }
        })
    }

    private fun findGoModuleRoot(file: VirtualFile): VirtualFile? {
        var current = file.parent
        while (current != null) {
            val goMod = current.findChild("go.mod")
            if (goMod != null && !goMod.isDirectory) {
                return current
            }
            current = current.parent
        }
        return null
    }

    private fun processFile(file: VirtualFile, clipboardContent: StringBuilder) {
        try {
            val content = com.intellij.openapi.application.ApplicationManager.getApplication()
                .runReadAction<String> {
                    String(file.contentsToByteArray())
                }

            clipboardContent.append("// File: ${file.path}\n")
                .append(content)
                .append("\n\n")

        } catch (e: Exception) {
            println("Error reading file ${file.path}: ${e.message}")
        }
    }

    private fun isGoFile(file: VirtualFile): Boolean {
        return !file.isDirectory && file.extension?.lowercase() == "go"
    }

    private fun notify(project: Project?, message: String, type: NotificationType) {
        notificationGroup.createNotification(message, type).notify(project)
    }

    override fun update(e: AnActionEvent) {
        val project = e.project
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE)
        e.presentation.isEnabledAndVisible = project != null && file != null && isGoFile(file)
    }
}
