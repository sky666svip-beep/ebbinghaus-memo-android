package com.ebbinghaus.memo.ui.component

import android.annotation.SuppressLint
import android.graphics.Color
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.ebbinghaus.memo.core.util.MathTextPreprocessor
import kotlinx.coroutines.delay
import org.json.JSONObject

/**
 * WebView 初始化延迟（毫秒）。
 *
 * 构造 WebView 会拉起 Chromium 引擎（数百毫秒级），若在导航转场动画期间执行会抢占主线程造成掉帧。
 * 因此延后到转场结束后再创建，延迟期间以 Unicode 降级文本占位。
 */
private const val WEBVIEW_INIT_DELAY_MS = 120L

/**
 * 内容高度变化时的平滑过渡时长（毫秒）。
 *
 * WebView 高度经 JS 异步测量回调，若直接跳变会带动外层卡片高度突变，
 * 表现为展开/收起笔记区时的抖动与闪烁；用动画过渡可使其平滑稳定。
 */
private const val MATH_VIEW_SIZE_ANIM_MS = 160

/**
 * 原生集成 KaTeX 离线公式排版组件
 *
 * 支持标准 LaTeX（如 \frac、\sqrt、\sum）以及自适应未包裹 $ 的逻辑/数学公式（如 \le、\iff、\oplus 等）。
 * 自动适配 Material 3 浅色/深色主题，动态测量内容高度。
 *
 * **性能策略（两级渲染路径）**：
 * 1. 文本既不含 LaTeX 也不含 Markdown → 走原生 [Text] 渲染，**完全不创建 WebView**（零 Chromium 开销）；
 * 2. 文本含 LaTeX 或 Markdown → 延迟 [WEBVIEW_INIT_DELAY_MS] 后创建 WebView，
 *    交由 marked（Markdown）+ KaTeX（公式）排版；延迟期间先用 Unicode 降级文本占位，
 *    避免转场卡顿与白屏闪烁。
 *
 * **渲染管线**（实现见 `assets/katex/math_renderer.html`）：
 * 公式区间保护 → Markdown 转 HTML → 还原公式 → KaTeX 渲染。
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun MathView(
    text: String,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 16.sp,
    minHeight: Dp = 28.dp
) {
    // ── 快路径：既不含 LaTeX 也不含 Markdown 的纯文本，直接用原生 Text 渲染 ──
    // 这是消除列表/详情/复习页切换卡顿的关键：绝大多数知识点为纯文本，
    // 走此路径可彻底规避 WebView 构造（Chromium 引擎初始化）带来的数百毫秒开销。
    val needsRich = remember(text) { MathTextPreprocessor.needsRichRendering(text) }
    if (!needsRich) {
        Text(
            text = remember(text) { MathTextPreprocessor.formatToUnicode(text) },
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = fontSize),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = modifier
                .fillMaxWidth()
                .heightIn(min = minHeight)
        )
        return
    }

    val isDark = isSystemInDarkTheme()
    val textColor = MaterialTheme.colorScheme.onSurface
    val textColorHex = remember(isDark, textColor) {
        String.format("#%06X", 0xFFFFFF and textColor.toArgb())
    }

    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var isPageLoaded by remember { mutableStateOf(false) }

    // 延迟创建 WebView：先让导航转场动画执行完毕，再初始化 Chromium 引擎，
    // 避免在动画期间抢占主线程导致掉帧。
    var webViewReady by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(WEBVIEW_INIT_DELAY_MS)
        webViewReady = true
    }

    val safeText = remember(text) {
        JSONObject.quote(text)
    }

    fun triggerRender(webView: WebView) {
        val jsCode = "updateContent($safeText, '$textColorHex', ${fontSize.value.toInt()});"
        webView.evaluateJavascript(jsCode, null)
    }

    LaunchedEffect(text, textColorHex, fontSize, isPageLoaded) {
        val wv = webViewRef
        if (wv != null && isPageLoaded) {
            triggerRender(wv)
        }
    }

    // animateContentSize 让「占位文本 → KaTeX 实际排版」的高度变化平滑过渡。
    // WebView 的高度由 JS 异步回调测量，若直接跳变会带动外层卡片高度突变，
    // 表现为展开笔记区时的抖动/闪烁。
    Box(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = tween(MATH_VIEW_SIZE_ANIM_MS))
    ) {
        // 仅当延迟期结束后才真正构造 WebView（Chromium 初始化），避免转场掉帧
        if (webViewReady) {
            AndroidView(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = minHeight, max = 2000.dp),
                factory = { context ->
                    WebView(context).apply {
                        setBackgroundColor(Color.TRANSPARENT)
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = false
                        settings.allowFileAccess = true
                        settings.allowContentAccess = true
                        isVerticalScrollBarEnabled = false
                        isHorizontalScrollBarEnabled = false

                        // 保留高度回调接口以兼容 math_renderer.html 的调用。
                        // ⚠️ 刻意不写入 Compose State：WebView 采用自然高度布局（无需外部测量），
                        // 而任何由此产生的状态变化都可能与 update{} / animateContentSize
                        // 形成「渲染 → 回调 → 重组 → 再渲染」的无限循环。
                        addJavascriptInterface(object {
                            @JavascriptInterface
                            fun onHeightChanged(heightPx: Float) {
                                // no-op：保留接口签名，不参与布局
                            }
                        }, "AndroidBridge")

                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                isPageLoaded = true
                                if (view != null) {
                                    triggerRender(view)
                                }
                            }
                        }

                        loadUrl("file:///android_asset/katex/math_renderer.html")
                        webViewRef = this
                    }
                },
                update = { webView ->
                    // 仅在实例真正变化时更新引用，避免每次重组都写入 Compose State
                    if (webViewRef !== webView) {
                        webViewRef = webView
                    }
                    // ⚠️ 切勿在此调用 triggerRender！
                    // update{} 会在每次重组时执行；而渲染会触发 JS 高度回调 → animateContentSize
                    // 尺寸动画 → 再次重组 → 再次渲染，形成「渲染 → 回调 → 重组 → 再渲染」的无限循环
                    // （表现为界面持续反复刷新、输入异常）。
                    // 内容更新统一由上方 LaunchedEffect(text, textColorHex, fontSize, isPageLoaded) 驱动。
                }
            )
        }

        // WebView 未就绪（延迟期）或 KaTeX 页面尚未加载完成时，
        // 以 Unicode 降级文本占位，避免白屏闪烁
        if (!webViewReady || !isPageLoaded) {
            Text(
                text = MathTextPreprocessor.formatToUnicode(text),
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = fontSize),
                color = textColor
            )
        }
    }
}
