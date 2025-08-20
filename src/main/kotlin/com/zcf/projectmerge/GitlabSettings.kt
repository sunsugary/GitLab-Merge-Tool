package com.zcf.projectmerge

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.application.ApplicationManager

@State(name = "GitlabSettings", storages = [Storage("GitlabSettings.xml")])
@Service
class GitlabSettings : PersistentStateComponent<GitlabSettings.State> {
    class State {
        var token: String? = null
        var baseUrl: String = "http://code.jms.com" // 不带结尾斜杠
        var topGroup: String = "project"            // 一级目录路径
        var debugLog: Boolean = false
    }

    private var state = State()

    override fun getState(): State = state
    override fun loadState(state: State) { this.state = state }

    var token: String?
        get() = state.token
        set(v) { state.token = v }

    var baseUrl: String
        get() = state.baseUrl
        set(v) { state.baseUrl = v.trimEnd('/') }

    var topGroup: String
        get() = state.topGroup
        set(v) { state.topGroup = v.trim('/') }

    var debugLog: Boolean
        get() = state.debugLog
        set(v) { state.debugLog = v }

    companion object {
        fun getInstance(): GitlabSettings = ApplicationManager.getApplication().getService(GitlabSettings::class.java)
    }
}