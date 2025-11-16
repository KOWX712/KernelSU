package me.weishu.kernelsu.ui.webui

import android.annotation.SuppressLint
import android.app.Activity
import android.app.ActivityManager
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Base64
import android.webkit.ConsoleMessage
import android.webkit.MimeTypeMap
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.webkit.WebViewAssetLoader
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.launch
import me.weishu.kernelsu.ui.util.createRootShell
import me.weishu.kernelsu.ui.viewmodel.SuperUserViewModel
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@SuppressLint("SetJavaScriptEnabled")
class WebUIActivity : ComponentActivity() {
    private lateinit var webviewInterface: WebViewInterface
    private lateinit var moduleId: String
    private var rootShell: Shell? = null
    private lateinit var insets: Insets
    private lateinit var fileChooserLauncher: ActivityResultLauncher<Intent>
    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private lateinit var saveFileLauncher: ActivityResultLauncher<Intent>
    private var pendingDownloadData: ByteArray? = null
    private var pendingDownloadSuggestedFilename: String? = null

    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {

        // Enable edge to edge
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }

        super.onCreate(savedInstanceState)

        setContent {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                LoadingIndicator()
            }
        }

        val superUserViewModel = ViewModelProvider(this)[SuperUserViewModel::class.java]
        lifecycleScope.launch {
            if (superUserViewModel.isAppListEmpty) {
                superUserViewModel.fetchAppList()
            }
            setupWebView()
        }

        fileChooserLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val data = result.data
                var uris: Array<Uri>? = null
                data?.dataString?.let {
                    uris = arrayOf(it.toUri())
                }
                data?.clipData?.let { clipData ->
                    uris = Array(clipData.itemCount) { i -> clipData.getItemAt(i).uri }
                }
                filePathCallback?.onReceiveValue(uris)
                filePathCallback = null
            } else {
                filePathCallback?.onReceiveValue(null)
                filePathCallback = null
            }
        }

        saveFileLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                result.data?.data?.let { uri ->
                    pendingDownloadData?.let { dataToSave ->
                        try {
                            contentResolver.openOutputStream(uri)?.use { outputStream ->
                                outputStream.write(dataToSave)
                            }
                            Toast.makeText(this, "Saved: ${pendingDownloadSuggestedFilename ?: "File"}", Toast.LENGTH_LONG).show()
                        } catch (e: Exception) {
                            Toast.makeText(this, "Error saving file: ${e.message}", Toast.LENGTH_LONG).show()
                        } finally {
                            pendingDownloadData = null
                            pendingDownloadSuggestedFilename = null
                        }
                    }
                }
            } else {
                Toast.makeText(this, "Save cancelled", Toast.LENGTH_SHORT).show()
                pendingDownloadData = null
                pendingDownloadSuggestedFilename = null
            }
        }
    }

    private fun setupWebView() {
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
        WebView.setWebContentsDebuggingEnabled(prefs.getBoolean("enable_web_debugging", false))

        val moduleDir = "/data/adb/modules/${moduleId}"
        val webRoot = File("${moduleDir}/webroot")
        val rootShell = createRootShell(true).also { this.rootShell = it }
        insets = Insets(0, 0, 0, 0)

        val webViewAssetLoader = WebViewAssetLoader.Builder()
            .setDomain("mui.kernelsu.org")
            .addPathHandler(
                "/",
                SuFilePathHandler(this, webRoot, rootShell) { insets }
            )
            .build()

        val webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest
            ): WebResourceResponse? {
                val url = request.url

                // Handle ksu://icon/[packageName] to serve app icon via WebView
                if (url.scheme.equals("ksu", ignoreCase = true) && url.host.equals("icon", ignoreCase = true)) {
                    val packageName = url.path?.substring(1)
                    if (!packageName.isNullOrEmpty()) {
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

            override fun onPageFinished(view: WebView, url: String?) {
                super.onPageFinished(view, url)
                injectCss(view)
            }
        }

        val webView = WebView(this).apply {
            setBackgroundColor(Color.TRANSPARENT)
            val density = resources.displayMetrics.density

            ViewCompat.setOnApplyWindowInsetsListener(this) { _, windowInsets ->
                val inset = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
                insets = Insets(
                    top = (inset.top / density).toInt(),
                    bottom = (inset.bottom / density).toInt(),
                    left = (inset.left / density).toInt(),
                    right = (inset.right / density).toInt()
                )
                WindowInsetsCompat.CONSUMED
            }
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = false
            webviewInterface = WebViewInterface(this@WebUIActivity, this, moduleDir)
            addJavascriptInterface(webviewInterface, "ksu")
            setWebViewClient(webViewClient)
            webChromeClient = object : WebChromeClient() {
                override fun onShowFileChooser(
                    webView: WebView?,
                    filePathCallback: ValueCallback<Array<Uri>>?,
                    fileChooserParams: FileChooserParams?
                ): Boolean {
                    this@WebUIActivity.filePathCallback = filePathCallback
                    val intent = fileChooserParams?.createIntent() ?: Intent(Intent.ACTION_GET_CONTENT).apply { type = "*/*" }
                    if (fileChooserParams?.mode == FileChooserParams.MODE_OPEN_MULTIPLE) {
                        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                    }
                    try {
                        fileChooserLauncher.launch(intent)
                    } catch (_: Exception) {
                        this@WebUIActivity.filePathCallback?.onReceiveValue(null)
                        this@WebUIActivity.filePathCallback = null
                        return false
                    }
                    return true
                }

                override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                    consoleMessage?.let {
                        val message = it.message()
                        val prefix = "ksuBlobData:"
                        if (message.startsWith(prefix)) {
                            val jsonData = message.substring(prefix.length)
                            try {
                                val json = JSONObject(jsonData)
                                val dataUrl = json.getString("dataUrl")
                                val mimeType = json.getString("mimeType")
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                    saveDataUrlToDownloads(dataUrl, mimeType)
                                }
                                return true
                            } catch (_: org.json.JSONException) {
                                Toast.makeText(this@WebUIActivity, "Error parsing blob data from console", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                    return super.onConsoleMessage(consoleMessage)
                }
            }

            setDownloadListener { url, _, contentDisposition, mimeType, _ ->
                if (url.startsWith("blob:")) {
                    val escContentDisposition = contentDisposition?.replace("'", "\\'") ?: ""
                    val escMimeType = mimeType?.replace("'", "\\'") ?: ""
                    val script = """
                        javascript:(function() {
                            fetch('$url')
                                .then(response => response.blob())
                                .then(blob => {
                                    const reader = new FileReader();
                                    reader.onloadend = function() {
                                        const payload = {
                                            dataUrl: reader.result,
                                            contentDisposition: '${escContentDisposition}',
                                            mimeType: '${escMimeType}'
                                        };
                                        console.log('ksuBlobData:' + JSON.stringify(payload));
                                    };
                                    reader.readAsDataURL(blob);
                                });
                        })();
                    """.trimIndent()
                    evaluateJavascript(script, null)
                } else if (url.startsWith("data:")) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        saveDataUrlToDownloads(url, mimeType)
                    }
                } else {
                    Toast.makeText(this@WebUIActivity, "Cannot download from this URL type", Toast.LENGTH_SHORT).show()
                }
            }
            loadUrl("https://mui.kernelsu.org/index.html")
        }

        setContentView(webView)
    }

    private fun injectCss(webView: WebView) {
        val insets = insets.css
        val insetsJs = """
            javascript:(function() {
                style = document.createElement('style');
                document.head.appendChild(style);
                style.innerHTML = ${JSONObject.quote(insets)};
            })();
        """.trimIndent()
        webView.evaluateJavascript(insetsJs, null)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val colors = MonetColorsProvider.getColorsCss(this)
            val colorJs = """
                javascript:(function() {
                    style = document.createElement('style');
                    document.head.appendChild(style);
                    style.innerHTML = ${JSONObject.quote(colors)};
                })();
            """.trimIndent()
            webView.evaluateJavascript(colorJs, null)
        }
    }

    private fun extractMimeTypeAndBase64Data(dataUrl: String): Pair<String, String>? {
        val prefix = "data:"
        if (!dataUrl.startsWith(prefix)) return null
        val commaIndex = dataUrl.indexOf(',')
        if (commaIndex == -1) return null
        val header = dataUrl.substring(prefix.length, commaIndex)
        val data = dataUrl.substring(commaIndex + 1)
        val mimeType = header.substringBefore(';', header).ifEmpty { "application/octet-stream" }
        return Pair(mimeType, data)
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun saveDataUrlToDownloads(dataUrl: String, mimeTypeFromListener: String) {
        val (mimeType, base64Data) = extractMimeTypeAndBase64Data(dataUrl) ?: run {
            Toast.makeText(this, "Invalid data URL", Toast.LENGTH_SHORT).show()
            return
        }

        val finalMimeType = if (mimeType == "application/octet-stream" && mimeTypeFromListener.isNotBlank()) mimeTypeFromListener else mimeType
        var extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(finalMimeType)
        if (extension != null && !extension.startsWith(".")) {
            extension = ".$extension"
        }
        if (extension.isNullOrEmpty()) {
            extension = ""
        }
        
        val sdf = SimpleDateFormat("yyyy-MM-dd_HHmmss", Locale.getDefault())
        val formattedDate = sdf.format(Date(System.currentTimeMillis()))
        val fileName = "${moduleId}_${formattedDate}${extension}"

        try {
            val decodedData = Base64.decode(base64Data, Base64.DEFAULT)

            pendingDownloadData = decodedData
            pendingDownloadSuggestedFilename = fileName

            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = finalMimeType
                putExtra(Intent.EXTRA_TITLE, fileName)
            }
            saveFileLauncher.launch(intent)

        } catch (e: Exception) {
            Toast.makeText(this, "Error preparing file for saving: ${e.message}", Toast.LENGTH_LONG).show()
            pendingDownloadData = null
            pendingDownloadSuggestedFilename = null
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        runCatching { rootShell?.close() }
    }
}