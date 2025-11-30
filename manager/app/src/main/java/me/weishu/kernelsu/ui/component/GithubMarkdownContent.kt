package me.weishu.kernelsu.ui.component

import android.graphics.Color
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import me.weishu.kernelsu.ksuApp
import okhttp3.Headers.Companion.toHeaders
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okio.IOException
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets

@Composable
fun GithubMarkdownContent(content: String) {
    val dir = if (LocalLayoutDirection.current == LayoutDirection.Rtl) "rtl" else "ltr"
    fun cssColorFromArgb(argb: androidx.compose.ui.graphics.Color): String {
        val r = (argb.red * 255).toInt()
        val g = (argb.green * 255).toInt()
        val b = (argb.blue * 255).toInt()
        val a = argb.alpha
        return "rgba(${r},${g},${b},${"%.3f".format(a)})"
    }

    val contentColorCss = cssColorFromArgb(MaterialTheme.colorScheme.onSurface)
    val linkColorCss = cssColorFromArgb(MaterialTheme.colorScheme.primary)

    val html = """
        <!DOCTYPE html>
        <html>
        <head>
          <meta charset='utf-8'/>
          <meta name='viewport' content='width=device-width, initial-scale=1'/>
          <style>
            :root { --ksu-color: ${contentColorCss}; --ksu-link: ${linkColorCss}; }
            html, body { margin:0; padding:0; background:transparent; color: var(--ksu-color); font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Helvetica,Arial,sans-serif,'Apple Color Emoji','Segoe UI Emoji'; }
            * { color: var(--ksu-color); }
            a { color: var(--ksu-link) !important; }
            img, video { max-width:100%; height:auto; }
            pre { white-space: pre-wrap; }
            code { color: var(--ksu-color); }
            h1, h2, h3, h4, h5, h6, p, span, li { color: var(--ksu-color); }
            .ksu { padding: 0; margin: 0; }
            .ksu > :first-child { margin-top: 0 !important; }
            .ksu > :last-child { margin-bottom: 0 !important; }
            h1, h2, h3, h4, h5, h6 { margin: 0.25em 0; }
            p, ul, ol, pre, blockquote { margin: 0.25em 0; }
            ul, ol { padding-left: 1.2em; }
          </style>
        </head>
        <body dir='${dir}'><div class='ksu'>${content}</div></body>
        </html>
    """.trimIndent()

    AndroidView(
        factory = { context ->
            WebView(context).apply {
                setBackgroundColor(Color.TRANSPARENT)
                settings.apply {
                    offscreenPreRaster = true
                    domStorageEnabled = true
                    mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    allowContentAccess = false
                    allowFileAccessFromFileURLs = true
                    allowFileAccess = false
                    setSupportZoom(false)
                    cacheMode = WebSettings.LOAD_CACHE_ELSE_NETWORK
                    textZoom = 80
                }
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                        // Open external URLs via system
                        val url = request.url.toString()
                        try {
                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, url.toUri())
                            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(intent)
                        } catch (_: Throwable) {
                        }
                        return true
                    }

                    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
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
                            val header = reply.header("content-type", "image/*;charset=utf-8")
                            val contentTypes = header?.split(";\\s*".toRegex()) ?: emptyList()
                            val mimeType = contentTypes.firstOrNull() ?: "image/*"
                            val charset = contentTypes.getOrNull(1)?.split("=\\s*".toRegex())?.getOrNull(1) ?: "utf-8"
                            val body = reply.body ?: return null
                            WebResourceResponse(
                                mimeType,
                                charset,
                                body.byteStream()
                            )
                        } catch (e: IOException) {
                            WebResourceResponse(
                                "text/html",
                                "utf-8",
                                ByteArrayInputStream(android.util.Log.getStackTraceString(e).toByteArray(StandardCharsets.UTF_8))
                            )
                        }
                    }
                }
                loadDataWithBaseURL("https://github.com", html, "text/html", StandardCharsets.UTF_8.name(), null)
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .clipToBounds(),
        update = { webView ->
            webView.loadDataWithBaseURL("https://github.com", html, "text/html", StandardCharsets.UTF_8.name(), null)
        }
    )
}
