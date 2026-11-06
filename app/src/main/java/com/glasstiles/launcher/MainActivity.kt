package com.glasstiles.launcher

import android.annotation.SuppressLint
import android.app.Activity
import android.app.SearchManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.provider.Settings
import android.provider.Telephony
import android.telecom.TelecomManager
import android.util.Base64
import android.view.View
import android.view.WindowInsets
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebViewAssetLoader
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/**
 * Glass Tiles home screen.
 * A full-screen WebView shows the glass-tiles home page (assets/home/index.html);
 * this activity supplies the real installed apps, their icons, and launches them.
 */
class MainActivity : Activity() {

    private lateinit var web: WebView
    private val main = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()
    private val iconCache = ConcurrentHashMap<String, String>()

    @Volatile private var appsJson = "[]"
    private var insetTop = 0
    private var insetBottom = 0

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            intent.data?.schemeSpecificPart?.let { iconCache.remove(it) }
            loadApps()
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        goEdgeToEdge()

        val loader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()

        web = WebView(this)
        web.setBackgroundColor(Color.BLACK)
        web.overScrollMode = View.OVER_SCROLL_NEVER
        web.isVerticalScrollBarEnabled = false
        web.isHorizontalScrollBarEnabled = false
        web.isHapticFeedbackEnabled = true
        with(web.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            textZoom = 100
            setSupportZoom(false)
            builtInZoomControls = false
        }

        web.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest
            ): WebResourceResponse? = loader.shouldInterceptRequest(request.url)

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                if (request.url.host == ASSET_HOST) return false
                openUrl(request.url.toString())
                return true
            }

            override fun onPageFinished(view: WebView, url: String) {
                pushInsets()
                loadApps()
            }
        }

        web.addJavascriptInterface(Bridge(), "Android")

        web.setOnApplyWindowInsetsListener { _, insets ->
            val bars = readInsets(insets)
            insetTop = bars.first
            insetBottom = bars.second
            pushInsets()
            insets
        }

        setContentView(web)
        web.loadUrl("https://$ASSET_HOST/assets/home/index.html")

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(packageReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(packageReceiver, filter)
        }
    }

    override fun onResume() {
        super.onResume()
        if (::web.isInitialized) {
            web.evaluateJavascript("window.onResumeHome&&onResumeHome()", null)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Home pressed while already home: close folders and go to the first page.
        web.evaluateJavascript("window.goHome&&goHome()", null)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // A home screen never closes; back just closes folders and menus.
        web.evaluateJavascript("window.onBack&&onBack()", null)
    }

    override fun onDestroy() {
        try { unregisterReceiver(packageReceiver) } catch (_: Exception) { }
        io.shutdown()
        web.destroy()
        super.onDestroy()
    }

    // ---------- screen edges ----------

    @Suppress("DEPRECATION")
    private fun goEdgeToEdge() {
        if (Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(false)
        } else {
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        }
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= 29) window.isNavigationBarContrastEnforced = false
    }

    @Suppress("DEPRECATION")
    private fun readInsets(insets: WindowInsets): Pair<Int, Int> {
        return if (Build.VERSION.SDK_INT >= 30) {
            val b = insets.getInsets(WindowInsets.Type.systemBars())
            Pair(b.top, b.bottom)
        } else {
            Pair(insets.systemWindowInsetTop, insets.systemWindowInsetBottom)
        }
    }

    private fun pushInsets() {
        if (!::web.isInitialized) return
        val d = resources.displayMetrics.density
        web.evaluateJavascript("window.setInsets&&setInsets(${insetTop / d},${insetBottom / d})", null)
    }

    // ---------- apps ----------

    private fun loadApps() {
        if (io.isShutdown) return
        io.execute {
            val pm = packageManager
            val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val infos = pm.queryIntentActivities(launcherIntent, 0)
            val seen = HashSet<String>()
            val items = ArrayList<JSONObject>()
            for (ri in infos) {
                val pkg = ri.activityInfo.packageName
                if (pkg == packageName || !seen.add(pkg)) continue
                val icon = iconCache.getOrPut(pkg) {
                    try { iconData(ri.loadIcon(pm)) } catch (_: Exception) { "" }
                }
                val o = JSONObject()
                o.put("pkg", pkg)
                o.put("label", ri.loadLabel(pm).toString())
                o.put("cat", ri.activityInfo.applicationInfo.category)
                o.put("icon", icon)
                items.add(o)
            }
            items.sortBy { it.optString("label").lowercase() }
            val arr = JSONArray()
            items.forEach { arr.put(it) }
            appsJson = arr.toString()
            main.post {
                if (!isDestroyed) web.evaluateJavascript("window.onAppsChanged&&onAppsChanged()", null)
            }
        }
    }

    private fun iconData(d: Drawable): String {
        val size = 128
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        d.setBounds(0, 0, size, size)
        d.draw(c)
        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
        bmp.recycle()
        return "data:image/png;base64," + Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }

    private fun resolvePkg(i: Intent): String {
        val ri = packageManager.resolveActivity(i, PackageManager.MATCH_DEFAULT_ONLY) ?: return ""
        val p = ri.activityInfo?.packageName ?: return ""
        return if (p == "android" || p.contains("resolver")) "" else p
    }

    private fun dockJson(): String {
        val dialer = (try {
            getSystemService(TelecomManager::class.java)?.defaultDialerPackage
        } catch (_: Exception) { null }) ?: resolvePkg(Intent(Intent.ACTION_DIAL))
        val sms = (try {
            Telephony.Sms.getDefaultSmsPackage(this)
        } catch (_: Exception) { null }) ?: resolvePkg(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:")))
        val browser = resolvePkg(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com")))
        val camera = resolvePkg(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA))
        return JSONArray(listOf(dialer, sms, browser, camera)).toString()
    }

    private fun safeStart(i: Intent): Boolean {
        return try {
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(i)
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun openUrl(url: String): Boolean = safeStart(Intent(Intent.ACTION_VIEW, Uri.parse(url)))

    private fun launchPkg(pkg: String) {
        val i = packageManager.getLaunchIntentForPackage(pkg) ?: return
        i.addFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        safeStart(i)
    }

    // ---------- bridge for the home page ----------

    inner class Bridge {
        @JavascriptInterface fun getApps(): String = appsJson

        @JavascriptInterface fun getDock(): String = dockJson()

        @JavascriptInterface fun launch(pkg: String) { main.post { launchPkg(pkg) } }

        @JavascriptInterface fun appInfo(pkg: String) {
            main.post { safeStart(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg"))) }
        }

        @JavascriptInterface fun uninstall(pkg: String) {
            main.post { safeStart(Intent(Intent.ACTION_DELETE, Uri.parse("package:$pkg"))) }
        }

        @JavascriptInterface fun openLink(url: String) { main.post { openUrl(url) } }

        @JavascriptInterface fun webSearch(q: String) {
            main.post {
                val ok = safeStart(Intent(Intent.ACTION_WEB_SEARCH).putExtra(SearchManager.QUERY, q))
                if (!ok) openUrl("https://www.google.com/search?q=" + Uri.encode(q))
            }
        }

        @JavascriptInterface fun askClaude(q: String) {
            main.post { openUrl("https://claude.ai/new?q=" + Uri.encode(q)) }
        }

        @JavascriptInterface fun homeSettings() {
            main.post { safeStart(Intent(Settings.ACTION_HOME_SETTINGS)) }
        }
    }

    companion object {
        private const val ASSET_HOST = "appassets.androidplatform.net"
    }
}
