package me.weishu.kernelsu.ui.viewmodel

import android.util.Log
import android.widget.Toast
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.weishu.kernelsu.R
import me.weishu.kernelsu.ksuApp
import me.weishu.kernelsu.ui.util.HanziToPinyin
import me.weishu.kernelsu.ui.util.isNetworkAvailable
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

class ModuleRepoViewModel : ViewModel() {

    companion object {
        private const val TAG = "ModuleRepoViewModel"
        private const val MODULES_URL = "https://modules.kernelsu.org/modules.json"
    }

    @Immutable
    data class Author(
        val name: String,
        val link: String,
    )

    @Immutable
    data class ReleaseAsset(
        val name: String,
        val downloadUrl: String,
        val size: Long
    )

    @Immutable
    data class RepoModule(
        val moduleId: String,
        val moduleName: String,
        val authors: String,
        val authorList: List<Author>,
        val summary: String,
        val metamodule: Boolean,
        val stargazerCount: Int,
        val updatedAt: String,
        val createdAt: String,
        val latestRelease: String,
        val latestReleaseTime: String,
        val latestVersionCode: Int,
        val latestAsset: ReleaseAsset?,
    )

    private var _modules = mutableStateOf<List<RepoModule>>(emptyList())
    val modules: State<List<RepoModule>> = _modules

    var search by mutableStateOf(TextFieldValue(""))

    val filteredModules by derivedStateOf {
        val searchText = search.text
        modules.value.filter { module ->
            module.moduleId.contains(searchText, true) || module.moduleName.contains(searchText, true) ||
            HanziToPinyin.getInstance().toPinyinString(module.moduleName).contains(searchText, true) ||
            module.summary.contains(searchText, true) || module.authors.contains(searchText, true)
        }
    }

    var isRefreshing by mutableStateOf(false)
        private set

    fun refresh() {
        viewModelScope.launch {
            val netAvailable = isNetworkAvailable(ksuApp)
            withContext(Dispatchers.Main) { isRefreshing = true }
            val parsed = withContext(Dispatchers.IO) { if (!netAvailable) null else fetchModulesInternal() }
            withContext(Dispatchers.Main) {
                if (parsed != null) {
                    _modules.value = parsed
                } else {
                    Toast.makeText(
                        ksuApp,
                        ksuApp.getString(R.string.network_offline), Toast.LENGTH_SHORT
                    ).show()
                }
                isRefreshing = false
            }
        }
    }

    private fun fetchModulesInternal(): List<RepoModule> {
        return runCatching {
            val request = Request.Builder().url(MODULES_URL).build()
            ksuApp.okhttpClient.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return emptyList()
                val body = resp.body.string()
                val json = JSONArray(body)
                (0 until json.length()).mapNotNull { idx ->
                    val item = json.optJSONObject(idx) ?: return@mapNotNull null
                    parseRepoModule(item)
                }
            }
        }.getOrElse {
            Log.e(TAG, "fetch modules failed", it)
            emptyList()
        }
    }

    private fun parseRepoModule(item: JSONObject): RepoModule? {
        val moduleId = item.optString("moduleId", "")
        if (moduleId.isEmpty()) return null
        val moduleName = item.optString("moduleName", "")
        val authorsArray = item.optJSONArray("authors")
        val authorList = if (authorsArray != null) {
            (0 until authorsArray.length())
                .mapNotNull { idx ->
                    val authorObj = authorsArray.optJSONObject(idx) ?: return@mapNotNull null
                    val name = authorObj.optString("name", "").trim()
                    var link = authorObj.optString("link", "").trim()
                    if (link.startsWith("`") && link.endsWith("`") && link.length >= 2) {
                        link = link.substring(1, link.length - 1)
                    }
                    if (name.isEmpty()) null else Author(name = name, link = link)
                }
        } else {
            emptyList()
        }
        val authors = if (authorList.isNotEmpty()) authorList.joinToString(", ") { it.name } else item.optString("authors", "")
        val summary = item.optString("summary", "")
        val metamodule = item.optBoolean("metamodule", false)
        val stargazerCount = item.optInt("stargazerCount", 0)
        val updatedAt = item.optString("updatedAt", "")
        val createdAt = item.optString("createdAt", "")

        var latestRelease = ""
        var latestReleaseTime = ""
        var latestVersionCode = 0
        var latestAsset: ReleaseAsset? = null
        val lr = item.optJSONObject("latestRelease")
        if (lr != null) {
            val lrName = lr.optString("name", lr.optString("version", ""))
            val lrTime = lr.optString("time", "")
            var lrUrl = lr.optString("downloadUrl", "")
            lrUrl = lrUrl.trim().let {
                var s = it
                if (s.startsWith("`") && s.endsWith("`") && s.length >= 2) {
                    s = s.substring(1, s.length - 1)
                }
                s
            }
            val vcAny = lr.opt("versionCode")
            latestVersionCode = when (vcAny) {
                is Number -> vcAny.toInt()
                is String -> vcAny.toIntOrNull() ?: 0
                else -> 0
            }
            latestRelease = lrName
            latestReleaseTime = lrTime
            if (lrUrl.isNotEmpty()) {
                val fileName = lrUrl.substringAfterLast('/')
                latestAsset = ReleaseAsset(name = fileName, downloadUrl = lrUrl, size = 0L)
            }
        }

        return RepoModule(
            moduleId = moduleId,
            moduleName = moduleName,
            authors = authors,
            authorList = authorList,
            summary = summary,
            metamodule = metamodule,
            stargazerCount = stargazerCount,
            updatedAt = updatedAt,
            createdAt = createdAt,
            latestRelease = latestRelease,
            latestReleaseTime = latestReleaseTime,
            latestVersionCode = latestVersionCode,
            latestAsset = latestAsset,
        )
    }
}
