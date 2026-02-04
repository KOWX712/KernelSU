package me.weishu.kernelsu.ui.webui

import android.annotation.SuppressLint
import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.webkit.JsPromptResult
import android.webkit.JsResult
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebViewAssetLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.weishu.kernelsu.R
import me.weishu.kernelsu.ui.util.createRootShell
import me.weishu.kernelsu.ui.viewmodel.ModuleViewModel
import me.weishu.kernelsu.ui.viewmodel.SuperUserViewModel
import java.io.File

private const val WEB_DOMAIN = "mui.kernelsu.org"
private const val KSU_SCHEME = "ksu"
private const val ICON_HOST = "icon"
private const val DOWNLOAD_JS = """
    (function() {
        if (window.ksu_download_enabled) return;
        window.ksu_download_enabled = true;
        const blobMap = new Map();
        const orgCreate = URL.createObjectURL;
        URL.createObjectURL = (obj) => {
            const url = orgCreate(obj);
            if (obj instanceof Blob) blobMap.set(url, obj);
            return url;
        };
        const orgRevoke = URL.revokeObjectURL;
        URL.revokeObjectURL = (url) => {
            setTimeout(() => blobMap.delete(url), 10000);
            orgRevoke(url);
        };
        const handleDownload = async (a) => {
            const urlParsed = new URL(a.href, location.href);
            const url = urlParsed.href;
            const fileName = a.download || url.split("/").pop().split("?")[0] || "download.bin";
            const isInternal = urlParsed.hostname === 'mui.kernelsu.org';
            if (url.startsWith('blob:') || url.startsWith('data:') || isInternal) {
                const blob = (url.startsWith('blob:') && blobMap.has(url)) ? blobMap.get(url) : await (await fetch(url)).blob();
                if (blob.size > 16 * 1024 * 1024) {
                    console.error("File too large, please use FileOutputStreamInterface instead.");
                    return;
                }
                const reader = new FileReader();
                reader.onload = () => {
                    ksu_download.save(reader.result.split(',')[1], fileName, blob.type);
                };
                reader.readAsDataURL(blob);
            } else {
                ksu_download.download(url, null, null);
            }
        };
        document.addEventListener("click", (e) => {
            const a = e.target.closest("a[download]");
            if (a) {
                e.preventDefault();
                handleDownload(a);
            }
        }, true);
        const orgClick = HTMLAnchorElement.prototype.click;
        HTMLAnchorElement.prototype.click = function () {
            this.hasAttribute("download") ? handleDownload(this) : orgClick.apply(this, arguments);
        };
    })();
"""

@SuppressLint("SetJavaScriptEnabled")
internal suspend fun prepareWebView(
    activity: Activity,
    moduleId: String,
    webUIState: WebUIState,
) {
    withContext(Dispatchers.IO) {
        val viewModel = ModuleViewModel()
        if (viewModel.moduleList.isEmpty()) {
            viewModel.loadModuleList()
        }

        val moduleInfo = viewModel.moduleList.find { info -> info.id == moduleId }

        if (moduleInfo == null) {
            withContext(Dispatchers.Main) {
                webUIState.uiEvent = WebUIEvent.Error(activity.getString(R.string.no_such_module, moduleId))
            }
            return@withContext
        }

        if (!moduleInfo.hasWebUi || !moduleInfo.enabled || moduleInfo.remove) {
            withContext(Dispatchers.Main) {
                webUIState.uiEvent = WebUIEvent.Error(activity.getString(R.string.module_unavailable, moduleInfo.name))
            }
            return@withContext
        }

        webUIState.moduleName = moduleInfo.name
        webUIState.modDir = "/data/adb/modules/${moduleId}"

        if (SuperUserViewModel.apps.isEmpty()) {
            SuperUserViewModel().fetchAppList()
        }
        val shell = createRootShell(true)
        webUIState.rootShell = shell

        withContext(Dispatchers.Main) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                @Suppress("DEPRECATION")
                activity.setTaskDescription(ActivityManager.TaskDescription("KernelSU - ${moduleInfo.name}"))
            } else {
                val taskDescription = ActivityManager.TaskDescription.Builder()
                    .setLabel("KernelSU - ${moduleInfo.name}")
                    .build()
                activity.setTaskDescription(taskDescription)
            }

            val webView = WebView(activity)
            webView.setBackgroundColor(Color.TRANSPARENT)

            val prefs = activity.getSharedPreferences("settings", Context.MODE_PRIVATE)
            val enableWebDebugging = prefs.getBoolean("enable_web_debugging", false)
            WebView.setWebContentsDebuggingEnabled(enableWebDebugging)

            webView.settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                allowFileAccess = false
            }

            val webRoot = File("${webUIState.modDir}/webroot")
            val webViewAssetLoader = WebViewAssetLoader.Builder()
                .setDomain(WEB_DOMAIN)
                .addPathHandler(
                    "/",
                    SuFilePathHandler(activity, webRoot, shell, { webUIState.currentInsets }, { enable -> webUIState.isInsetsEnabled = enable })
                )
                .build()

            // WebViewClient
            webView.webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                    val url = request.url
                    if (url.scheme.equals(KSU_SCHEME, ignoreCase = true) && url.host.equals(ICON_HOST, ignoreCase = true)) {
                        val packageName = url.path?.substring(1)
                        if (!packageName.isNullOrEmpty() && packageName.matches(Regex("[a-zA-Z0-9._]+"))) {
                            val icon = AppIconUtil.loadAppIconSync(activity, packageName, 512)
                            if (icon != null) {
                                val stream = java.io.ByteArrayOutputStream()
                                icon.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream)
                                return WebResourceResponse("image/png", null, java.io.ByteArrayInputStream(stream.toByteArray()))
                            }
                        }
                    }
                    return webViewAssetLoader.shouldInterceptRequest(url)
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    if (enableWebDebugging) {
                        view?.evaluateJavascript(erudaConsole(activity), null)
                        view?.evaluateJavascript("eruda.init();", null)
                    }
                    view?.evaluateJavascript(DOWNLOAD_JS, null)
                }

                override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                    webUIState.webCanGoBack = view?.canGoBack() ?: false
                    if (webUIState.isInsetsEnabled) webUIState.webView?.evaluateJavascript(webUIState.currentInsets.js, null)
                    super.doUpdateVisitedHistory(view, url, isReload)
                }
            }

            // WebChromeClient
            webView.webChromeClient = object : WebChromeClient() {
                override fun onJsAlert(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
                    if (message == null || result == null) return false
                    webUIState.uiEvent = WebUIEvent.ShowAlert(message, result)
                    return true
                }

                override fun onJsConfirm(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
                    if (message == null || result == null) return false
                    webUIState.uiEvent = WebUIEvent.ShowConfirm(message, result)
                    return true
                }

                override fun onJsPrompt(view: WebView?, url: String?, message: String?, defaultValue: String?, result: JsPromptResult?): Boolean {
                    if (message == null || result == null || defaultValue == null) return false
                    webUIState.uiEvent = WebUIEvent.ShowPrompt(message, defaultValue, result)
                    return true
                }

                override fun onShowFileChooser(
                    webView: WebView?, filePathCallback: ValueCallback<Array<Uri>>?, fileChooserParams: FileChooserParams?
                ): Boolean {
                    webUIState.filePathCallback?.onReceiveValue(null)
                    webUIState.filePathCallback = filePathCallback

                    val intent = fileChooserParams?.createIntent() ?: Intent(Intent.ACTION_GET_CONTENT).apply { type = "*/*" }
                    if (fileChooserParams?.mode == FileChooserParams.MODE_OPEN_MULTIPLE) {
                        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                    }
                    webUIState.uiEvent = WebUIEvent.ShowFileChooser(intent)
                    return true
                }
            }

            // JS Interface
            val webviewInterface = WebViewInterface(webUIState)
            webUIState.webviewInterface = webviewInterface
            webUIState.webView = webView
            webView.addJavascriptInterface(webviewInterface, "ksu")

            val downloadInterface = DownloadInterface(webUIState)
            webView.addJavascriptInterface(downloadInterface, "ksu_download")

            webView.setDownloadListener { url, _, contentDisposition, mimetype, _ ->
                downloadInterface.download(url, contentDisposition, mimetype)
            }

            webUIState.uiEvent = WebUIEvent.WebViewReady
        }
    }
}

private fun erudaConsole(context: Context): String {
    return context.assets.open("eruda.min.js").bufferedReader().use { it.readText() }
}