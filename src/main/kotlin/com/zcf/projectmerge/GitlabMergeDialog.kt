package com.zcf.projectmerge

import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.table.JBTable
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import javax.swing.*
import javax.swing.table.DefaultTableCellRenderer
import javax.swing.table.DefaultTableModel
import javax.swing.table.TableCellEditor
import javax.swing.text.JTextComponent


class GitlabMergeDialog(private val api: GitlabApi, private val topGroup: String) : DialogWrapper(true) {

    private val subgroupList = JBList<String>()
    private val projectTable = object : JBTable() {
        override fun getCellEditor(row: Int, column: Int): TableCellEditor? {
            if (column == 0) return DefaultCellEditor(JCheckBox())
            return super.getCellEditor(row, column)
        }
    }

    private var currentProjects: List<MutableMap<String, Any?>> = emptyList()

    private val tableModel = object : DefaultTableModel(
        arrayOf("Selected", "Project"), 0
    ) {
        override fun isCellEditable(row: Int, column: Int): Boolean = column == 0
        override fun getColumnClass(column: Int): Class<*> = if (column == 0) Boolean::class.java else String::class.java
    }

    // 全局源分支 ComboBox
    val sourceBranchComboBox = ComboBox<String>().apply {
        isEditable = true
        editor.item = ""
        preferredSize = Dimension(300, 35)
        val editorComponent = editor.editorComponent as JTextComponent

        editorComponent.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                if (e.keyCode == KeyEvent.VK_ENTER) {
                    e.consume() // 阻止回车冒泡触发 OK
                    val text = editorComponent.text.trim()

                    // 获取当前选中的项目
                    val selectedProjects = selectedProjects // 来自 GitlabMergeDialog.selectedProjects

                    val allBranches = mutableSetOf<String>()

                    selectedProjects.forEach { project ->
                        val projectId = (project["id"] as Number).toString()
                        try {
                            val branches = api.getBranches(projectId, text).map { it["name"].toString() }
                            allBranches.addAll(branches)
                        } catch (_: Exception) {
                            // 忽略某个项目的异常
                        }
                    }
                    val uniqueBranches = allBranches.toSortedSet()
                    // 更新下拉框模型
                    val model = DefaultComboBoxModel<String>()
                    uniqueBranches.forEach { model.addElement(it) }
                    this@apply.model = model

                    // 保留输入的内容
                    editorComponent.text = text
                    editorComponent.caretPosition = text.length

                    if (uniqueBranches.isNotEmpty()) this@apply.showPopup()
                }
            }
        })
    }

    private val settings = GitlabSettings.getInstance()

    // 从配置取 targetBranch，用逗号分隔后转成数组
    val targetBranches = settings.targetBranch
        .split(",")               // 按逗号切分
        .map { it.trim() }        // 去掉前后空格
        .filter { it.isNotEmpty() }

    // 下拉框
    val targetBranchComboBox = ComboBox(targetBranches.toTypedArray())

    val selectedProjects: List<Map<String, Any?>>
        get() = mutableListOf<Map<String, Any?>>().apply {
            for (i in 0 until tableModel.rowCount) {
                val isSelected = tableModel.getValueAt(i, 0) as Boolean
                if (isSelected) add(currentProjects[i])
            }
        }

    init {
        title = "GitLab Merge Tool"
        init()
        setupProjectTable()
        loadSubgroups()
    }

    private fun setupProjectTable() {
        projectTable.model = tableModel
        projectTable.selectionModel.selectionMode = ListSelectionModel.SINGLE_SELECTION

        // 设置列宽
        projectTable.columnModel.getColumn(0).preferredWidth = 50
        projectTable.columnModel.getColumn(1).preferredWidth = 400

        // 复选框列居中显示
        projectTable.columnModel.getColumn(0).cellRenderer = object : DefaultTableCellRenderer() {
            override fun getTableCellRendererComponent(
                table: JTable, value: Any?, isSelected: Boolean, hasFocus: Boolean,
                row: Int, column: Int
            ): java.awt.Component {
                val checkBox = JCheckBox()
                checkBox.isSelected = value as? Boolean ?: false
                checkBox.horizontalAlignment = SwingConstants.CENTER
                return checkBox
            }
        }
    }

    private fun loadSubgroups() {
        try {
            val subgroups = api.getSubgroups(topGroup)
            val subgroupNames = subgroups.map { it["full_path"].toString() }
            subgroupList.setListData(subgroupNames.toTypedArray())

            subgroupList.addListSelectionListener { e ->
                if (!e.valueIsAdjusting && !subgroupList.isSelectionEmpty) {
                    val selectedSubgroup = subgroupList.selectedValue
                    selectedSubgroup?.let { loadProjects(it) }
                }
            }

            if (subgroupNames.isNotEmpty()) subgroupList.selectedIndex = 0
        } catch (ex: Exception) {
            Notifier.error(null, "Failed to load subgroups: ${ex.message}")
        }
    }

    private fun loadProjects(subgroupFullPath: String) {
        try {
            currentProjects = api.getGroupProjects(subgroupFullPath).map { it.toMutableMap() }
            tableModel.rowCount = 0
            currentProjects.forEach { project ->
                tableModel.addRow(arrayOf(false, project["name"].toString()))
            }
        } catch (ex: Exception) {
            Notifier.error(null, "Failed to load projects: ${ex.message}")
        }
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(BorderLayout())
        val leftPanel = JBScrollPane(subgroupList).apply { preferredSize = Dimension(200, 0) }
        val rightPanel = JBScrollPane(projectTable)

        val splitPane = JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, rightPanel).apply {
            resizeWeight = 0.25
            preferredSize = Dimension(800, 500)
        }

        panel.add(splitPane, BorderLayout.CENTER)

        // 底部统一选择源分支，目标分支固定
        val bottomPanel = JPanel()
        bottomPanel.add(JLabel("Source Branch:"))
        bottomPanel.add(sourceBranchComboBox)
        bottomPanel.add(JLabel("Target Branch:"))
        bottomPanel.add(targetBranchComboBox)
        panel.add(bottomPanel, BorderLayout.SOUTH)

        return panel
    }

    override fun doOKAction() {
        if (projectTable.isEditing) projectTable.cellEditor.stopCellEditing()
        super.doOKAction()
    }
}


