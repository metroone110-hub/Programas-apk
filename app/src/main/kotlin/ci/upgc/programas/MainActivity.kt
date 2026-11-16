package ci.upgc.programas

import android.Manifest
import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Bundle
import android.os.Message
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout

class MainActivity : AppCompatActivity() {

    private lateinit var rootLayout: FrameLayout
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar
    private lateinit var offlineOverlay: LinearLayout

    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private var pendingGeoCallback: GeolocationPermissions.Callback? = null
    private var pendingGeoOrigin: String? = null
    private var pendingWebPermissionRequest: PermissionRequest? = null
    private var activeAuthDialog: Dialog? = null
    private var mainFrameFailed = false

    private val fileChooserLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val callback = filePathCallback ?: return@registerForActivityResult
            val uris = WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data)
            callback.onReceiveValue(uris)
            filePathCallback = null
        }

    private val locationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
            val granted = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
            val origin = pendingGeoOrigin
            val cb = pendingGeoCallback
            pendingGeoOrigin = null
            pendingGeoCallback = null
            if (origin != null && cb != null) {
                cb.invoke(origin, granted, false)
            }
        }

    private val cameraPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            val req = pendingWebPermissionRequest
            pendingWebPermissionRequest = null
            if (req != null) {
                if (granted) {
                    req.grant(req.resources)
                } else {
                    req.deny()
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupUi()
        configureWebView()
        setupBackNavigation()

        val initialUrl = intent?.dataString?.takeIf {
            it.startsWith("https://") && it.contains(BuildConfig.APP_TARGET_HOST)
        } ?: BuildConfig.APP_TARGET_URL

        loadTargetUrl(initialUrl)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val deepLink = intent.dataString
        if (!deepLink.isNullOrBlank() && deepLink.startsWith("https://") && deepLink.contains(BuildConfig.APP_TARGET_HOST)) {
            loadTargetUrl(deepLink)
        }
    }

    private fun setupUi() {
        rootLayout = FrameLayout(this).apply {
            setBackgroundColor(Color.WHITE)
        }

        ViewCompat.setOnApplyWindowInsetsListener(rootLayout) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        webView = WebView(this)
        swipeRefresh = SwipeRefreshLayout(this).apply {
            isEnabled = true
            setColorSchemeColors(Color.parseColor("#EA580C"))
            setOnChildScrollUpCallback { _, _ -> webView.scrollY > 0 }
            addView(
                webView,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
            setOnRefreshListener {
                if (isNetworkAvailable()) {
                    mainFrameFailed = false
                    offlineOverlay.visibility = View.GONE
                    webView.reload()
                } else {
                    isRefreshing = false
                }
            }
        }

        progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            visibility = View.GONE
            progressTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#EA580C"))
        }

        offlineOverlay = buildOfflineOverlay()

        rootLayout.addView(
            swipeRefresh,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        rootLayout.addView(
            progressBar,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                dp(3)
            ).apply { gravity = Gravity.TOP }
        )
        rootLayout.addView(
            offlineOverlay,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        setContentView(rootLayout)
    }

    private fun buildOfflineOverlay(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#FAFAFA"))
            val pad = dp(32)
            setPadding(pad, pad, pad, pad)
            visibility = View.GONE

            val title = TextView(context).apply {
                text = "Connexion Internet requise"
                setTextColor(Color.parseColor("#111827"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
                gravity = Gravity.CENTER
                setPadding(0, 0, 0, dp(10))
            }

            val subtitle = TextView(context).apply {
                text = "Impossible de joindre Programas. Vérifiez votre connexion mobile ou Wi-Fi puis réessayez."
                setTextColor(Color.parseColor("#6B7280"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                gravity = Gravity.CENTER
                setPadding(0, 0, 0, dp(24))
            }

            val retryBtn = Button(context).apply {
                text = "Réessayer"
                setBackgroundColor(Color.parseColor("#EA580C"))
                setTextColor(Color.WHITE)
                setPadding(dp(28), dp(12), dp(28), dp(12))
                setOnClickListener {
                    mainFrameFailed = false
                    visibility = View.GONE
                    loadTargetUrl(webView.url ?: BuildConfig.APP_TARGET_URL)
                }
            }

            addView(title)
            addView(subtitle)
            addView(retryBtn)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureWebView() {
        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(webView, true)

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            javaScriptCanOpenWindowsAutomatically = true
            setSupportMultipleWindows(true)
            setGeolocationEnabled(true)
            mediaPlaybackRequiresUserGesture = false
            allowFileAccess = true
            allowContentAccess = true
            loadWithOverviewMode = true
            useWideViewPort = true
            cacheMode = WebSettings.LOAD_DEFAULT
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            // Retire "; wv" du User-Agent afin que Google OAuth autorise la connexion native
            userAgentString = userAgentString.replace("; wv", "").replace("wv)", ")")
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val uri = request?.url ?: return false
                val scheme = uri.scheme?.lowercase() ?: ""
                val host = uri.host?.lowercase() ?: ""

                // Liens externes : WhatsApp, appel, e-mail, Wave / Jeko / Mobile Money
                if (scheme != "http" && scheme != "https") {
                    return launchExternalUri(uri)
                }

                val isExternalIntentHost = host.contains("wa.me") ||
                    host.contains("whatsapp.com") ||
                    host.contains("pay.wave.com")
                if (isExternalIntentHost) {
                    return launchExternalUri(uri)
                }

                // Rester dans la WebView pour Programas, Firebase Auth et Google Maps
                val stayInApp = host == BuildConfig.APP_TARGET_HOST ||
                    host.endsWith(".workers.dev") ||
                    host.endsWith(".firebaseapp.com") ||
                    host.endsWith(".web.app") ||
                    host == "accounts.google.com" ||
                    host.endsWith(".google.com") ||
                    host.endsWith(".gstatic.com") ||
                    host.endsWith(".googleusercontent.com") ||
                    host.endsWith(".jeko.africa")

                if (stayInApp) {
                    return false
                }

                return launchExternalUri(uri)
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                mainFrameFailed = false
                offlineOverlay.visibility = View.GONE
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                swipeRefresh.isRefreshing = false
                CookieManager.getInstance().flush()
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                if (request?.isForMainFrame == true) {
                    mainFrameFailed = true
                    swipeRefresh.isRefreshing = false
                    offlineOverlay.visibility = View.VISIBLE
                }
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                if (newProgress in 1..99) {
                    progressBar.visibility = View.VISIBLE
                    progressBar.progress = newProgress
                } else {
                    progressBar.visibility = View.GONE
                }
            }

            override fun onGeolocationPermissionsShowPrompt(
                origin: String?,
                callback: GeolocationPermissions.Callback?
            ) {
                if (origin == null || callback == null) return
                val fineOk = ContextCompat.checkSelfPermission(
                    this@MainActivity,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
                val coarseOk = ContextCompat.checkSelfPermission(
                    this@MainActivity,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
                if (fineOk || coarseOk) {
                    callback.invoke(origin, true, false)
                } else {
                    pendingGeoOrigin = origin
                    pendingGeoCallback = callback
                    locationPermissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                    )
                }
            }

            override fun onPermissionRequest(request: PermissionRequest?) {
                if (request == null) return
                val needsCamera = request.resources.contains(PermissionRequest.RESOURCE_VIDEO_CAPTURE)
                if (!needsCamera) {
                    request.grant(request.resources)
                    return
                }
                val camGranted = ContextCompat.checkSelfPermission(
                    this@MainActivity,
                    Manifest.permission.CAMERA
                ) == PackageManager.PERMISSION_GRANTED
                if (camGranted) {
                    request.grant(request.resources)
                } else {
                    pendingWebPermissionRequest = request
                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                }
            }

            override fun onShowFileChooser(
                webView: WebView?,
                callback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                filePathCallback?.onReceiveValue(null)
                filePathCallback = callback
                return try {
                    val intent = fileChooserParams?.createIntent() ?: Intent(Intent.ACTION_GET_CONTENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = "*/*"
                    }
                    fileChooserLauncher.launch(intent)
                    true
                } catch (_: Exception) {
                    filePathCallback = null
                    false
                }
            }

            override fun onCreateWindow(
                view: WebView?,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: Message?
            ): Boolean {
                val transport = resultMsg?.obj as? WebView.WebViewTransport ?: return false
                val popupWebView = WebView(this@MainActivity)
                CookieManager.getInstance().setAcceptThirdPartyCookies(popupWebView, true)
                popupWebView.settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    javaScriptCanOpenWindowsAutomatically = true
                    userAgentString = webView.settings.userAgentString
                }

                val dialog = Dialog(this@MainActivity, android.R.style.Theme_DeviceDefault_Light_NoActionBar).apply {
                    setContentView(popupWebView)
                    setOnDismissListener {
                        popupWebView.destroy()
                        if (activeAuthDialog === this) activeAuthDialog = null
                    }
                }
                activeAuthDialog?.dismiss()
                activeAuthDialog = dialog

                popupWebView.webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(v: WebView?, req: WebResourceRequest?): Boolean {
                        return false
                    }
                }
                popupWebView.webChromeClient = object : WebChromeClient() {
                    override fun onCloseWindow(window: WebView?) {
                        dialog.dismiss()
                    }
                }

                dialog.show()
                transport.webView = popupWebView
                resultMsg.sendToTarget()
                return true
            }
        }
    }

    private fun loadTargetUrl(url: String) {
        if (!isNetworkAvailable()) {
            offlineOverlay.visibility = View.VISIBLE
            return
        }
        offlineOverlay.visibility = View.GONE
        webView.loadUrl(url)
    }

    private fun launchExternalUri(uri: Uri): Boolean {
        return try {
            val intent = if (uri.scheme == "intent") {
                Intent.parseUri(uri.toString(), Intent.URI_INTENT_SCHEME)
            } else {
                Intent(Intent.ACTION_VIEW, uri)
            }
            startActivity(intent)
            true
        } catch (_: Exception) {
            true
        }
    }

    private fun isNetworkAvailable(): Boolean {
        val cm = getSystemService(ConnectivityManager::class.java) ?: return false
        val active = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(active) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun setupBackNavigation() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (activeAuthDialog?.isShowing == true) {
                    activeAuthDialog?.dismiss()
                    return
                }
                if (webView.canGoBack()) {
                    webView.goBack()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }

    private fun dp(value: Int): Int =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            resources.displayMetrics
        ).toInt()

    override fun onDestroy() {
        activeAuthDialog?.dismiss()
        webView.destroy()
        super.onDestroy()
    }
}
