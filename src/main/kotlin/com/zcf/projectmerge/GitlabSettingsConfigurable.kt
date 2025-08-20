package com.zcf.projectmerge

import com.intellij.openapi.options.Configurable
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.GridLayout
import javax.swing.*

class GitlabSettingsConfigurable : Configurable {
    private val settings = GitlabSettings.getInstance()

    private val tokenField = JPasswordField()
    private val baseUrlField = JTextField()
    private val topGroupField = JTextField()
    private val debugCheck = JCheckBox("Enable API debug log")

    private var panel: JPanel? = null

    override fun getDisplayName(): String = "GitLab Settings"

    override fun createComponent(): JComponent {
        panel = JPanel(GridBagLayout())
        val c = GridBagConstraints().apply {
            fill = GridBagConstraints.HORIZONTAL
            weightx = 1.0
            gridx = 0
            ipadx = 4
            ipady = 4
        }
        fun addRow(label: String, comp: JComponent, y: Int) {
            c.gridy = y; c.gridx = 0; c.weightx = 0.0
            panel!!.add(JLabel(label), c)
            c.gridx = 1; c.weightx = 1.0
            panel!!.add(comp, c)
        }

        baseUrlField.text = settings.baseUrl
        topGroupField.text = settings.topGroup
        tokenField.text = settings.token ?: ""
        debugCheck.isSelected = settings.debugLog

        addRow("Base URL:", baseUrlField, 0)
        addRow("Top Group:", topGroupField, 1)
        addRow("Access Token:", tokenField, 2)
        c.gridy = 3; c.gridx = 1; panel!!.add(debugCheck, c)

        return panel as JPanel
    }

    override fun isModified(): Boolean =
        baseUrlField.text.trimEnd('/') != settings.baseUrl ||
                topGroupField.text.trim('/') != settings.topGroup ||
                String(tokenField.password) != (settings.token ?: "") ||
                debugCheck.isSelected != settings.debugLog

    override fun apply() {
        settings.baseUrl = baseUrlField.text
        settings.topGroup = topGroupField.text
        settings.token = String(tokenField.password)
        settings.debugLog = debugCheck.isSelected
    }

    override fun reset() {
        baseUrlField.text = settings.baseUrl
        topGroupField.text = settings.topGroup
        tokenField.text = settings.token ?: ""
        debugCheck.isSelected = settings.debugLog
    }

    override fun disposeUIResources() { panel = null }
}