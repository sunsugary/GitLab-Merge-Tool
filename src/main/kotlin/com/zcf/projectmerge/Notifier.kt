package com.zcf.projectmerge

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project

object Notifier {
    fun info(project: Project?, content: String) =
        NotificationGroupManager.getInstance()
            .getNotificationGroup("GitLab Merge Tool")
            .createNotification(content, NotificationType.INFORMATION)
            .notify(project)

    fun warn(project: Project?, content: String) =
        NotificationGroupManager.getInstance()
            .getNotificationGroup("GitLab Merge Tool")
            .createNotification(content, NotificationType.WARNING)
            .notify(project)

    fun error(project: Project?, content: String) =
        NotificationGroupManager.getInstance()
            .getNotificationGroup("GitLab Merge Tool")
            .createNotification(content, NotificationType.ERROR)
            .notify(project)
}