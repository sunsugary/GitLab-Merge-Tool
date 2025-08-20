package com.zcf.projectmerge

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class GitlabApi(private val token: String, baseUrl: String) {
    private val base = baseUrl.trimEnd('/')
    private val api = "$base/api/v4"
    private val mapper = jacksonObjectMapper()
    private val debug = GitlabSettings.getInstance().debugLog

    private fun log(msg: String) { if (debug) println("[GitLabAPI] $msg") }

    private fun request(path: String, method: String = "GET", body: String? = null): String {
        val url = "$api$path"
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.requestMethod = method
        conn.setRequestProperty("PRIVATE-TOKEN", token)
        conn.setRequestProperty("Content-Type", "application/json")
        if (body != null) {
            conn.doOutput = true
            conn.outputStream.use { it.write(body.toByteArray()) }
        }
        val code = conn.responseCode
        val text = try {
            conn.inputStream.bufferedReader().readText()
        } catch (_: Exception) {
            conn.errorStream?.bufferedReader()?.readText() ?: ""
        }
        if (code !in 200..299) throw RuntimeException("GitLab API $code: $text")
        return text
    }

    private fun enc(path: String) = URLEncoder.encode(path, Charsets.UTF_8.name())

    // 取一级目录(组) 下的二级目录（子组）
    fun getSubgroups(topGroupPath: String): List<Map<String, Any?>> {
        val json = request("/groups/${enc(topGroupPath)}/subgroups")
        return mapper.readValue(json, List::class.java) as List<Map<String, Any?>>
    }

    // 取某个组（可为二级组 full_path）下的所有项目
    fun getGroupProjects(groupFullPath: String): List<Map<String, Any?>> {
        val json = request("/groups/${enc(groupFullPath)}/projects?include_subgroups=true&per_page=100")
        return mapper.readValue(json, List::class.java) as List<Map<String, Any?>>
    }


    // 取项目分支
    fun getBranches(projectId: String): List<Map<String, Any?>> {
        val json = request("/projects/$projectId/repository/branches?per_page=100")
        return mapper.readValue(json, List::class.java) as List<Map<String, Any?>>
    }

    // 创建 MR
    fun createMergeRequest(projectId: String, source: String, target: String, title: String? = null): Int {
        val t = title ?: "Merge $source into $target"
        val body = """{"source_branch":"$source","target_branch":"$target","title":"$t"}"""
        val json = request("/projects/$projectId/merge_requests", method = "POST", body = body)
        val map = mapper.readValue(json, Map::class.java)
        return (map["iid"] as Number).toInt()
    }

    // 合并 MR（接受）
    fun acceptMergeRequest(projectId: String, mrIid: Int) {
        request("/projects/$projectId/merge_requests/$mrIid/merge", method = "PUT")
    }

    // 取项目分支，可选按关键字过滤
    fun getBranches(projectId: String, search: String? = null): List<Map<String, Any?>> {
        val query = if (search.isNullOrBlank()) "?per_page=100" else "?per_page=100&search=${enc(search)}"
        val json = request("/projects/$projectId/repository/branches$query")
        return mapper.readValue(json, List::class.java) as List<Map<String, Any?>>
    }

    // 查询指定源分支的打开 MR
    fun getMergeRequests(projectId: String, sourceBranch: String, state: String = "opened"): List<Map<String, Any?>> {
        val json = request("/projects/$projectId/merge_requests?state=$state&source_branch=${enc(sourceBranch)}")
        return mapper.readValue(json, List::class.java) as List<Map<String, Any?>>
    }

    // 查询指定 MR
    fun getMergeRequest(projectId: String, mrIid: Int): Map<String, Any?> {
        val json = request("/projects/$projectId/merge_requests/$mrIid")
        return mapper.readValue(json, Map::class.java) as Map<String, Any?>
    }




}
