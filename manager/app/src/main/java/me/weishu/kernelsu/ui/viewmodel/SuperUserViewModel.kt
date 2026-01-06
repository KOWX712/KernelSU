package me.weishu.kernelsu.ui.viewmodel

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.DeadObjectException
import android.os.IBinder
import android.os.Parcelable
import android.os.RemoteException
import android.os.SystemClock
import android.util.Log
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.TextFieldValue
import androidx.core.content.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.topjohnwu.superuser.Shell
import com.topjohnwu.superuser.ipc.RootService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.parcelize.Parcelize
import me.weishu.kernelsu.IKsuInterface
import me.weishu.kernelsu.Natives
import me.weishu.kernelsu.ksuApp
import me.weishu.kernelsu.ui.KsuService
import me.weishu.kernelsu.ui.util.HanziToPinyin
import me.weishu.kernelsu.ui.util.KsuCli
import java.text.Collator
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class SuperUserViewModel : ViewModel() {

    companion object {
        private const val TAG = "SuperUserViewModel"
        private val appsLock = Any()
        var apps by mutableStateOf<List<AppInfo>>(emptyList())

        @JvmStatic
        fun getAppIconDrawable(context: Context, packageName: String): Drawable? {
            val appList = synchronized(appsLock) { apps }
            val appDetail = appList.find { it.packageName == packageName }
            return appDetail?.packageInfo?.applicationInfo?.loadIcon(context.packageManager)
        }
    }

    @Parcelize
    data class AppInfo(
        val label: String,
        val packageInfo: PackageInfo,
        val profile: Natives.Profile?,
    ) : Parcelable {
        val packageName: String
            get() = packageInfo.packageName
        val uid: Int
            get() = packageInfo.applicationInfo!!.uid

        val allowSu: Boolean
            get() = profile != null && profile.allowSu
        val hasCustomProfile: Boolean
            get() {
                if (profile == null) {
                    return false
                }

                return if (profile.allowSu) {
                    !profile.rootUseDefault
                } else {
                    !profile.nonRootUseDefault
                }
            }
    }

    var search by mutableStateOf(TextFieldValue(""))
    private val prefs = ksuApp.getSharedPreferences("settings", Context.MODE_PRIVATE)!!
    var showSystemApps by mutableStateOf(prefs.getBoolean("show_system_apps", false))
    var isRefreshing by mutableStateOf(false)
        private set

    var isNeedRefresh by mutableStateOf(false)
        private set

    fun markNeedRefresh() {
        isNeedRefresh = true
    }

    fun updateShowSystemApps(newValue: Boolean) {
        showSystemApps = newValue
        prefs.edit { putBoolean("show_system_apps", newValue) }
    }

    val isAppListEmpty: Boolean
        get() = synchronized(appsLock) { apps.isEmpty() }

    private val sortedList by derivedStateOf {
        val comparator = compareBy<AppInfo> {
            when {
                it.allowSu -> 0
                it.hasCustomProfile -> 1
                else -> 2
            }
        }.then(compareBy(Collator.getInstance(Locale.getDefault()), AppInfo::label))
        apps.sortedWith(comparator).also {
            isRefreshing = false
        }
    }

    val appList by derivedStateOf {
        val search = search.text
        sortedList.filter {
            search.isEmpty() || it.label.contains(search, true) || it.packageName.contains(
                search,
                true
            ) || HanziToPinyin.getInstance()
                .toPinyinString(it.label).contains(search, true)
        }.filter {
            it.uid == 2000
                    || showSystemApps
                    || it.allowSu
                    || it.hasCustomProfile
                    || it.packageInfo.applicationInfo!!.flags.and(ApplicationInfo.FLAG_SYSTEM) == 0
        }
    }

    private suspend inline fun connectKsuService(
        crossinline onDisconnect: () -> Unit = {}
    ): Pair<IBinder, ServiceConnection> = suspendCoroutine {
        val connection = object : ServiceConnection {
            override fun onServiceDisconnected(name: ComponentName?) {
                onDisconnect()
            }

            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                it.resume(binder as IBinder to this)
            }
        }

        val intent = Intent(ksuApp, KsuService::class.java)

        val task = RootService.bindOrTask(
            intent,
            Shell.EXECUTOR,
            connection,
        )
        val shell = KsuCli.SHELL
        task?.let { it1 -> shell.execTask(it1) }
    }

    private fun stopKsuService() {
        val intent = Intent(ksuApp, KsuService::class.java)
        RootService.stop(intent)
    }

    private fun filterAndSort(list: List<AppInfo>): List<AppInfo> {
        val comparator = compareBy<AppInfo> {
            when {
                it.allowSu -> 0
                it.hasCustomProfile -> 1
                else -> 2
            }
        }.then(compareBy(Collator.getInstance(Locale.getDefault()), AppInfo::label))
        return list.sortedWith(comparator).filter {
            it.uid == 2000
                    || showSystemApps
                    || it.allowSu
                    || it.hasCustomProfile
                    || it.packageInfo.applicationInfo!!.flags.and(ApplicationInfo.FLAG_SYSTEM) == 0
        }
    }

    suspend fun fetchAppList() {
        Mutex().withLock {
            withContext(Dispatchers.Main) { isRefreshing = true }

            val result = connectKsuService {
                Log.w(TAG, "KsuService disconnected")
            }

            val allPackagesSlice = withContext(Dispatchers.IO) {
                val pm = ksuApp.packageManager
                val start = SystemClock.elapsedRealtime()

                val binder = result.first
                val iface = IKsuInterface.Stub.asInterface(binder)
                val slice = try {
                    iface.getPackages(0)
                } catch (_: DeadObjectException) {
                    val retry = connectKsuService { Log.w(TAG, "KsuService disconnected") }
                    IKsuInterface.Stub.asInterface(retry.first).getPackages(0)
                } catch (_: RemoteException) {
                    val retry = connectKsuService { Log.w(TAG, "KsuService disconnected") }
                    IKsuInterface.Stub.asInterface(retry.first).getPackages(0)
                }

                val packages = slice.list
                val newApps = packages.map {
                    val appInfo = it.applicationInfo
                    val uid = appInfo!!.uid
                    val profile = Natives.getAppProfile(it.packageName, uid)
                    AppInfo(
                        label = appInfo.loadLabel(pm).toString(),
                        packageInfo = it,
                        profile = profile,
                    )
                }.filter {
                        val ai = it.packageInfo.applicationInfo!!
                        if (Build.VERSION.SDK_INT >= 29) !ai.isResourceOverlay else true
                    }

                val sortedFiltered = filterAndSort(newApps)

                Log.i(TAG, "load cost: ${SystemClock.elapsedRealtime() - start}")

                Pair(newApps, sortedFiltered)
            }

            withContext(Dispatchers.Main) {
                synchronized(appsLock) {
                    apps = allPackagesSlice.first
                }
                isRefreshing = false
                isNeedRefresh = false
                stopKsuService()
            }
        }
    }

    private suspend fun refreshAppList() {
        Mutex().withLock {
            val currentApps = synchronized(appsLock) { apps }
            if (currentApps.isEmpty()) return

            val updatedApps = withContext(Dispatchers.IO) {
                currentApps.map {
                    val profile = Natives.getAppProfile(it.packageName, it.uid)
                    it.copy(profile = profile)
                }
            }

            val sortedFiltered = withContext(Dispatchers.IO) {
                filterAndSort(updatedApps)
            }

            withContext(Dispatchers.Main) {
                synchronized(appsLock) {
                    apps = updatedApps
                }
                isNeedRefresh = false
            }
        }
    }

    fun loadAppList(force: Boolean = false) {
        viewModelScope.launch {
            if (force || apps.isEmpty()) {
                fetchAppList()
            } else {
                refreshAppList()
            }
        }
    }
}
