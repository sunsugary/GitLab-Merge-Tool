package com.zcf.projectmerge

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.ui.Messages

class GitlabMergeAction : AnAction("GitLab Merge") {

    override fun actionPerformed(e: AnActionEvent) {
        val ideaProject = e.project
        val settings = GitlabSettings.getInstance()

        val token = settings.token
        if (token.isNullOrBlank()) {
            Messages.showErrorDialog(
                ideaProject,
                "请在 设置 → 工具 → GitLab 设置 中配置Access Token",
                "配置错误"
            )
            return
        }

        val baseUrl = settings.baseUrl

        if (baseUrl.isBlank()) {
            Messages.showErrorDialog(
                ideaProject,
                "请在 设置 → 工具 → GitLab 设置 中配置Base Url",
                "配置错误"
            )
            return
        }

        val topGroup = settings.topGroup
        if (topGroup.isBlank()) {
            Messages.showErrorDialog(
                ideaProject,
                "请在 设置 → 工具 → GitLab 设置 中配置Top Group",
                "配置错误"
            )
            return
        }

        val api = GitlabApi(token, baseUrl)

        // 显示对话框
        val dialog = GitlabMergeDialog(api, topGroup)
        if (!dialog.showAndGet()) return

        val selectedProjects = dialog.selectedProjects
        if (selectedProjects.isEmpty()) {
            Messages.showWarningDialog(ideaProject, "请至少选择一个项目", "选择错误")
            return
        }

        // 统一源分支
        val sourceBranch = dialog.sourceBranchComboBox.editor.item.toString().trim()
        val targetBranch = "uat"

        if (sourceBranch.isBlank()) {
            Messages.showWarningDialog(ideaProject, "源分支不能为空", "配置错误")
            return
        }

        ProgressManager.getInstance().run(object : Task.Backgroundable(ideaProject, "执行合并操作", false) {
            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = false
                val total = selectedProjects.size
                var completed = 0

                selectedProjects.forEach { project ->
                    completed++
                    val projectName = project["name"].toString()
                    val projectId = (project["id"] as Number).toString()
                    val progress = completed.toDouble() / total
                    indicator.fraction = progress
                    indicator.text = "正在合并 $projectName ($sourceBranch → $targetBranch)"

                    try {
                        // 创建 MR，如果已存在则获取已有 MR
                        val mrIid = try {
                            api.createMergeRequest(
                                projectId,
                                sourceBranch,
                                targetBranch,
                                "自动合并: $sourceBranch → $targetBranch"
                            )
                        } catch (ex: RuntimeException) {
                            if (ex.message?.contains("Another open merge request") == true) {
                                // 查询已有 MR
                                api.getMergeRequests(projectId, sourceBranch)
                                    .firstOrNull()?.get("iid")?.toString()?.toInt()
                                    ?: throw ex
                            } else throw ex
                        }

                        if (mrIid != null) {
                            // 轮询 MR 状态，等待可合并
                            var canMerge = false
                            repeat(10) {
                                val mr = api.getMergeRequest(projectId, mrIid)
                                val status = mr["merge_status"]?.toString()
                                if (status == "can_be_merged") {
                                    canMerge = true
                                    return@repeat
                                }
                                Thread.sleep(500)
                            }

                            if (canMerge) {
                                api.acceptMergeRequest(projectId, mrIid)
                                Notifier.info(ideaProject, "✅ 成功: $projectName ($sourceBranch → $targetBranch)")
                            } else {
                                Notifier.warn(ideaProject, "⚠️ $projectName ($sourceBranch → $targetBranch) 当前不能合并，请手动处理")
                            }
                        }

                    } catch (ex: Exception) {
                        Notifier.error(ideaProject, "❌ 失败: $projectName - ${ex.message}")
                    }
                }
            }
        })
    }
}
