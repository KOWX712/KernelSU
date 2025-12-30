package me.weishu.kernelsu.ui.component

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.util.Log
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import androidx.webkit.WebViewAssetLoader
import me.weishu.kernelsu.ksuApp
import me.weishu.kernelsu.ui.theme.isInDarkTheme
import me.weishu.kernelsu.ui.util.cssColorFromArgb
import okhttp3.Headers.Companion.toHeaders
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okio.IOException
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets

@Composable
fun GithubMarkdown(
    content: String,
    containerColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.surfaceContainer,
    webViewState: MutableState<WebView?>? = null,
    isLoadedState: MutableState<Boolean> = remember { mutableStateOf(false) },
    progressState: MutableState<Float> = remember { mutableFloatStateOf(0f) }
) {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    val themeMode = prefs.getInt("color_mode", 0)
    val isDark = isInDarkTheme(themeMode)
    val dir = if (LocalLayoutDirection.current == LayoutDirection.Rtl) "rtl" else "ltr"

    val bgArgb = containerColor.toArgb()

    val bgDefault = cssColorFromArgb(bgArgb)
    val bgMuted = cssColorFromArgb(MaterialTheme.colorScheme.surfaceContainerHigh.toArgb())
    val bgNeutralMuted = cssColorFromArgb(MaterialTheme.colorScheme.surfaceDim.toArgb())
    val bgAttentionMuted = cssColorFromArgb(MaterialTheme.colorScheme.surfaceBright.toArgb())
    val fgLink = cssColorFromArgb(MaterialTheme.colorScheme.primary.toArgb())

    val previousContent = remember { mutableStateOf(content) }
    if (previousContent.value != content) {
        isLoadedState.value = false
        progressState.value = 0f
        previousContent.value = content
    }

    val cssHref = "https://appassets.androidplatform.net/assets/github-markdown.css"
    val html = """
        <!DOCTYPE html>
        <html>
        <head>
          <meta charset='utf-8'/>
          <meta name='viewport' content='width=device-width, initial-scale=1'/>
          <link rel="stylesheet" href="$cssHref" />
          <style>
            html, body { margin:0; padding:0; }
            img, video { max-width:100%; height:auto; }
            .markdown-body {
              padding: 0;
              padding-top: 8px;
              --bgColor-default: $bgDefault;
              --bgColor-muted: $bgMuted;
              --bgColor-neutral-muted: $bgNeutralMuted;
              --bgColor-attention-muted: $bgAttentionMuted;
              --fgColor-accent: $fgLink;
            }
          </style>
        </head>
        <body dir='${dir}'>
          <article class='markdown-body' data-theme='${if (isDark) "dark" else "light"}'>${content}</article>
        </body>
        </html>
    """.trimIndent()

    Column(modifier = Modifier.fillMaxSize().clipToBounds()) {
        AnimatedVisibility(
            visible = !isLoadedState.value,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                LinearProgressIndicator(
                    progress = { progressState.value },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        AndroidView(
            factory = { context ->
                webViewState?.value ?: WebView(context).apply {
                    setBackgroundColor(Color.TRANSPARENT)
                    isVerticalScrollBarEnabled = false
                    isHorizontalScrollBarEnabled = false
                    settings.apply {
                        offscreenPreRaster = true
                        domStorageEnabled = true
                        mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                        allowContentAccess = false
                        allowFileAccess = false
                        cacheMode = WebSettings.LOAD_CACHE_ELSE_NETWORK
                        textZoom = 90
                        setSupportZoom(false)
                        setGeolocationEnabled(false)
                    }
                    webChromeClient = object : WebChromeClient() {
                        override fun onProgressChanged(view: WebView?, newProgress: Int) {
                            progressState.value = newProgress / 100f
                            if (newProgress == 100) {
                                isLoadedState.value = true
                            }
                        }
                    }
                    webViewClient = object : WebViewClient() {
                        private val assetLoader = WebViewAssetLoader.Builder()
                            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context))
                            .build()

                        override fun shouldOverrideUrlLoading(
                            view: WebView, request: WebResourceRequest
                        ): Boolean {
                            val url = request.url.toString()
                            val intent = Intent(Intent.ACTION_VIEW, url.toUri())
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(intent)
                            return true
                        }

                        override fun shouldInterceptRequest(
                            view: WebView, request: WebResourceRequest
                        ): WebResourceResponse? {
                            assetLoader.shouldInterceptRequest(request.url)?.let { return it }
                            val scheme = request.url.scheme ?: return null
                            if (!scheme.startsWith("http")) return null
                            val client: OkHttpClient = ksuApp.okhttpClient
                            val call = client.newCall(
                                Request.Builder()
                                    .url(request.url.toString())
                                    .method(request.method, null)
                                    .headers(request.requestHeaders.toHeaders())
                                    .build()
                            )
                            return try {
                                val reply: Response = call.execute()
                                val header = reply.header("content-type", "text/plain; charset=utf-8")
                                val contentTypes = header?.split(";\\s*".toRegex()) ?: emptyList()
                                val mimeType = contentTypes.firstOrNull() ?: "image/*"
                                val charset = contentTypes.getOrNull(1)?.split("=\\s*".toRegex())?.getOrNull(1) ?: "utf-8"
                                val body = reply.body ?: return null
                                WebResourceResponse(mimeType, charset, body.byteStream())
                            } catch (e: IOException) {
                                WebResourceResponse(
                                    "text/html", "utf-8",
                                    ByteArrayInputStream(Log.getStackTraceString(e).toByteArray(StandardCharsets.UTF_8))
                                )
                            }
                        }
                    }
                    webViewState?.value = this
                }
            },
            update = { view ->
                if (!isLoadedState.value && content.isNotEmpty()) {
                    view.loadDataWithBaseURL(
                        "https://appassets.androidplatform.net", html,
                        "text/html", StandardCharsets.UTF_8.name(), null
                    )
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}
