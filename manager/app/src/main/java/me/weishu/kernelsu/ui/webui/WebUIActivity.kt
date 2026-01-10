package me.weishu.kernelsu.ui.webui

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.webkit.WebViewAssetLoader
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import me.weishu.kernelsu.ui.theme.KernelSUTheme
import me.weishu.kernelsu.ui.util.createRootShell
import me.weishu.kernelsu.ui.viewmodel.SuperUserViewModel
import java.io.File

@SuppressLint("SetJavaScriptEnabled")
class WebUIActivity : ComponentActivity() {
    companion object {
        private const val DOMAIN = "mui.kernelsu.org"
        private const val KSU_SCHEME = "ksu"
        private const val ICON_HOST = "icon"
    }

    private lateinit var webviewInterface: WebViewInterface
    private var webView: WebView? = null
    private lateinit var container: FrameLayout
    private var rootShell: Shell? = null
    @Volatile
    private var insets: Insets = Insets(0, 0, 0, 0)
    private var insetsContinuation: CancellableContinuation<Unit>? = null
    private var isInsetsEnabled = false
    private lateinit var fileChooserLauncher: ActivityResultLauncher<Intent>
    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private var downloadFilename: String? = null

    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {

        // Enable edge to edge
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }

        super.onCreate(savedInstanceState)

        setContent {
            KernelSUTheme {
                Box(
                    modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
                    contentAlignment = Alignment.Center
                ) {
                    LoadingIndicator()
                }
            }
        }

        lifecycleScope.launch {
            if (SuperUserViewModel.apps.isEmpty()) {
                SuperUserViewModel().fetchAppList()
            }
            setupWebView()
        }

        fileChooserLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            val uris: Array<Uri>? = when (result.resultCode) {
                RESULT_OK -> result.data?.let { data ->
                    when {
                        data.clipData != null -> {
                            Array(data.clipData!!.itemCount) { i ->
                                data.clipData!!.getItemAt(i).uri // Multiple files
                            }
                        }
                        data.data != null -> { arrayOf(data.data!!) } // Single file
                        else -> null
                    }
                }
                else -> null
            }
            filePathCallback?.onReceiveValue(uris)
            filePathCallback = null
        }
    }

    private fun erudaConsole(context: Context): String {
        return context.assets.open("eruda.min.js").bufferedReader().use { it.readText() }
    }

    private suspend fun setupWebView() {
        val moduleId = intent.getStringExtra("id")!!
        val name = intent.getStringExtra("name")!!
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            @Suppress("DEPRECATION")
            setTaskDescription(ActivityManager.TaskDescription("KernelSU - $name"))
        } else {
            val taskDescription = ActivityManager.TaskDescription.Builder().setLabel("KernelSU - $name").build()
            setTaskDescription(taskDescription)
        }

        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        val enableWebDebugging = prefs.getBoolean("enable_web_debugging", false)

        WebView.setWebContentsDebuggingEnabled(enableWebDebugging)

        val moduleDir = "/data/adb/modules/${moduleId}"
        val webRoot = File("${moduleDir}/webroot")
        val rootShell = createRootShell(true).also { this.rootShell = it }
        insets = Insets(0, 0, 0, 0)

        container = FrameLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }

        this.webView = WebView(this).apply {
            setBackgroundColor(Color.TRANSPARENT)
            val density = resources.displayMetrics.density

            ViewCompat.setOnApplyWindowInsetsListener(container) { view, windowInsets ->
                val inset = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
                insets = Insets(
                    top = (inset.top / density).toInt(),
                    bottom = (inset.bottom / density).toInt(),
                    left = (inset.left / density).toInt(),
                    right = (inset.right / density).toInt()
                )
                if (isInsetsEnabled) {
                    view.setPadding(0, 0, 0, 0)
                } else {
                    view.setPadding(inset.left, inset.top, inset.right, inset.bottom)
                }
                insetsContinuation?.resumeWith(Result.success(Unit))
                insetsContinuation = null
                WindowInsetsCompat.CONSUMED
            }
        }
        container.addView(this.webView)
        setContentView(container)

        if (insets == Insets(0, 0, 0, 0)) {
            suspendCancellableCoroutine<Unit> { cont ->
                insetsContinuation = cont
                cont.invokeOnCancellation {
                    insetsContinuation = null
                }
            }
        }

        val webViewAssetLoader = WebViewAssetLoader.Builder()
            .setDomain(DOMAIN)
            .addPathHandler(
                "/",
                SuFilePathHandler(this, webRoot, rootShell, { insets }, { enable -> enableInsets(enable) })
            )
            .build()

        val webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest
            ): WebResourceResponse? {
                val url = request.url

                // Handle ksu://icon/[packageName] to serve app icon via WebView
                if (url.scheme.equals(KSU_SCHEME, ignoreCase = true) && url.host.equals(ICON_HOST, ignoreCase = true)) {
                    val packageName = url.path?.substring(1)
                    if (!packageName.isNullOrEmpty() && packageName.matches(Regex("[a-zA-Z0-9._]+"))) {
                        val icon = AppIconUtil.loadAppIconSync(this@WebUIActivity, packageName, 512)
                        if (icon != null) {
                            val stream = java.io.ByteArrayOutputStream()
                            icon.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream)
                            val inputStream = java.io.ByteArrayInputStream(stream.toByteArray())
                            return WebResourceResponse("image/png", null, inputStream)
                        }
                    }
                }

                return webViewAssetLoader.shouldInterceptRequest(url)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                view?.evaluateJavascript("""
                    (function () {
                        document.addEventListener("click", async (e) => {
                            if (e.target.tagName !== "A" || !e.target.hasAttribute("download")) return;
                            const filename = e.target.getAttribute("download") || "download";
                            downloadInterface.setDownloadFilename(filename);

                            if (!e.target.href.startsWith("blob:")) return;
                            e.preventDefault();
                            const blob = await fetch(e.target.href).then((r) => r.blob());
                            const reader = new FileReader();
                            reader.onload = () => {
                                const temp = document.createElement("a");
                                temp.href = reader.result;
                                temp.download = filename;
                                temp.click();
                            };
                            reader.readAsDataURL(blob);
                        });
                    })();
                    """, null
                )
                if (enableWebDebugging) {
                    view?.evaluateJavascript(erudaConsole(this@WebUIActivity), null)
                    view?.evaluateJavascript("eruda.init();", null)
                }
            }
        }

        webView?.apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = false
            webviewInterface = WebViewInterface(this@WebUIActivity, this, moduleDir)
            addJavascriptInterface(webviewInterface, "ksu")
            addJavascriptInterface(object {
                @JavascriptInterface
                fun setDownloadFilename(filename: String) { downloadFilename = filename }
            }, "downloadInterface")
            setWebViewClient(webViewClient)
            webChromeClient = object : WebChromeClient() {
                override fun onShowFileChooser(
                    webView: WebView?,
                    filePathCallback: ValueCallback<Array<Uri>>?,
                    fileChooserParams: FileChooserParams?
                ): Boolean {
                    this@WebUIActivity.filePathCallback?.onReceiveValue(null)
                    this@WebUIActivity.filePathCallback = filePathCallback
                    val intent = fileChooserParams?.createIntent() ?: Intent(Intent.ACTION_GET_CONTENT).apply { type = "*/*" }
                    if (fileChooserParams?.mode == FileChooserParams.MODE_OPEN_MULTIPLE) {
                        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                    }
                    try {
                        fileChooserLauncher.launch(intent)
                    } catch (_: ActivityNotFoundException) {
                        filePathCallback?.onReceiveValue(null)
                        this@WebUIActivity.filePathCallback = null
                        return false
                    }
                    return true
                }
            }
            setDownloadListener { url, _, contentDisposition, mimetype, _ ->
                if (url.startsWith("data:")) {
                    // Parse data URL
                    val parts = url.substring(5).split(",", limit = 2)
                    if (parts.size == 2) {
                        val header = parts[0]
                        val data = parts[1]
                        val mimeType = header.split(";")[0]
                        val base64 = header.contains("base64")
                        var filename = downloadFilename ?: "download"
                        if (contentDisposition.isNotEmpty()) {
                            val cdParts = contentDisposition.split(";")
                            for (part in cdParts) {
                                if (part.trim().startsWith("filename=")) {
                                    filename = part.substring(part.indexOf("=") + 1).trim('"')
                                    break
                                }
                            }
                        }
                        if (!filename.contains(".")) {
                            val ext = MimeUtil.getExtensionFromMime(mimeType)
                            filename += ".$ext"
                        }
                        filename = sanitizeFilename(filename)
                        try {
                            when {
                                base64 -> saveContent(
                                    android.util.Base64.decode(data, android.util.Base64.DEFAULT),
                                    filename
                                )
                                else -> saveContent(
                                    java.net.URLDecoder.decode(data, "UTF-8"),
                                    filename
                                )
                            }
                            downloadFilename = null
                        } catch (e: Exception) {
                            Toast.makeText(this@WebUIActivity, "Failed to decode data: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    // Use DownloadManager for regular URLs
                    val request = DownloadManager.Request(url.toUri())
                    if (mimetype.isNotEmpty()) {
                        request.setMimeType(mimetype)
                    }
                    if (contentDisposition.isNotEmpty()) {
                        request.setTitle(contentDisposition)
                    }
                    request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, getFilename(contentDisposition))

                    val dm = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                    dm.enqueue(request)
                    Toast.makeText(this@WebUIActivity, "Download started", Toast.LENGTH_SHORT).show()
                }
            }
            loadUrl("https://mui.kernelsu.org/index.html")
        }
    }

    private fun saveContent(content: String, filename: String) {
        saveToDownloads(filename) { file ->
            file.writeText(content)
        }
    }

    private fun saveContent(content: ByteArray, filename: String) {
        saveToDownloads(filename) { file ->
            file.writeBytes(content)
        }
    }

    private inline fun saveToDownloads(filename: String, writeOperation: (File) -> Unit) {
        try {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!downloadsDir.exists()) downloadsDir.mkdirs()
            var file = File(downloadsDir, filename)
            val name = filename.substringBeforeLast('.')
            val ext = if (filename.contains('.')) ".${filename.substringAfterLast('.')}" else ""
            var counter = 1
            while (file.exists()) {
                val newFilename = "$name ($counter)$ext"
                file = File(downloadsDir, newFilename)
                counter++
            }
            writeOperation(file)
            Toast.makeText(this, "Downloaded to Downloads/${file.name}", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Download failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun sanitizeFilename(filename: String): String {
        return filename.replace(Regex("[/\\\\:*?\"<>|]"), "_").take(255)
    }

    private fun getFilename(contentDisposition: String): String {
        if (contentDisposition.isEmpty()) return "download"
        val cdParts = contentDisposition.split(";")
        for (part in cdParts) {
            if (part.trim().startsWith("filename=")) {
                return sanitizeFilename(part.substring(part.indexOf("=") + 1).trim('"'))
            }
        }
        return "download"
    }

    fun enableInsets(enable: Boolean = true) {
        runOnUiThread {
            if (isInsetsEnabled != enable) {
                isInsetsEnabled = enable
                ViewCompat.requestApplyInsets(container)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        runCatching {
            webviewInterface.destroy()
            webView?.destroy()
            rootShell?.close()
        }
    }
}
