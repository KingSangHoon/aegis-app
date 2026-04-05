package com.aegis.portal

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Message
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.webkit.*
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout

class MainActivity : AppCompatActivity() {

    companion object {
        private const val HOME_URL = "https://tkdgns.com"
        private val AEGIS_HOSTS = listOf(
            "tkdgns.com",
            "tradepilot.tkdgns.com",
            "monitor.tkdgns.com",
            "vault.tkdgns.com",
            "share.tkdgns.com",
            "claw.tkdgns.com",
            "ntfy.tkdgns.com"
        )
        private const val FILE_CHOOSER_CODE = 100
        private const val PERMISSION_CODE = 200
    }

    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private var fileCallback: ValueCallback<Array<Uri>>? = null
    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 상태바/네비바 색상
        window.apply {
            addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            statusBarColor = Color.parseColor("#080c10")
            navigationBarColor = Color.parseColor("#080c10")
        }

        // 권한 요청
        requestAllPermissions()

        // 레이아웃 (코드로 생성)
        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor("#080c10"))
            fitsSystemWindows = true
        }

        progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, 6
            )
            progressDrawable.setColorFilter(
                Color.parseColor("#00e5ff"),
                android.graphics.PorterDuff.Mode.SRC_IN
            )
            max = 100
            visibility = View.GONE
        }

        webView = WebView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.parseColor("#080c10"))
        }

        swipeRefresh = SwipeRefreshLayout(this).apply {
            addView(webView)
            setColorSchemeColors(Color.parseColor("#00e5ff"))
            setProgressBackgroundColorSchemeColor(Color.parseColor("#161b22"))
            setOnRefreshListener {
                webView.reload()
            }
        }

        root.addView(swipeRefresh)
        root.addView(progressBar)
        setContentView(root)

        // WebView 설정
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            allowFileAccess = true
            allowContentAccess = true
            loadWithOverviewMode = true
            useWideViewPort = true
            builtInZoomControls = true
            displayZoomControls = false
            setSupportMultipleWindows(true)
            setSupportZoom(true)
            javaScriptCanOpenWindowsAutomatically = true
            mediaPlaybackRequiresUserGesture = false
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            cacheMode = WebSettings.LOAD_DEFAULT
            userAgentString = "${userAgentString} AEGIS-App/1.0"

            // 파일 업로드
            allowFileAccessFromFileURLs = true
            allowUniversalAccessFromFileURLs = true
        }

        // 쿠키 허용
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, true)
        }

        // WebViewClient
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val url = request.url.toString()
                val host = request.url.host ?: ""

                // AEGIS 도메인은 앱 내부에서 열기
                if (AEGIS_HOSTS.any { host.endsWith(it) || host == it }) {
                    return false
                }

                // 외부 링크는 브라우저로
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                } catch (_: Exception) {}
                return true
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                progressBar.visibility = View.VISIBLE
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                progressBar.visibility = View.GONE
                swipeRefresh.isRefreshing = false
            }

            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                // 메인 페이지 로딩 실패 시 오프라인 메시지
                if (request?.isForMainFrame == true) {
                    view?.loadData(
                        """
                        <html>
                        <body style="background:#080c10;color:#8b949e;font-family:monospace;display:flex;
                            align-items:center;justify-content:center;height:100vh;text-align:center;">
                            <div>
                                <h1 style="color:#00e5ff;font-size:2em;">AEGIS</h1>
                                <p>서버에 연결할 수 없습니다</p>
                                <p style="font-size:.8em;">아래로 당겨서 재시도</p>
                            </div>
                        </body>
                        </html>
                        """.trimIndent(),
                        "text/html", "UTF-8"
                    )
                }
            }
        }

        // WebChromeClient
        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                progressBar.progress = newProgress
                if (newProgress >= 100) progressBar.visibility = View.GONE
            }

            // 파일 선택 (Share 업로드용)
            override fun onShowFileChooser(
                webView: WebView?,
                callback: ValueCallback<Array<Uri>>?,
                params: FileChooserParams?
            ): Boolean {
                fileCallback?.onReceiveValue(null)
                fileCallback = callback

                val intent = params?.createIntent() ?: return false
                try {
                    startActivityForResult(intent, FILE_CHOOSER_CODE)
                } catch (_: Exception) {
                    fileCallback = null
                    return false
                }
                return true
            }

            // 카메라/마이크 권한
            override fun onPermissionRequest(request: PermissionRequest?) {
                request?.grant(request.resources)
            }

            // 위치 권한
            override fun onGeolocationPermissionsShowPrompt(
                origin: String?,
                callback: GeolocationPermissions.Callback?
            ) {
                callback?.invoke(origin, true, false)
            }

            // 풀스크린 (영상 등)
            override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                customView = view
                customViewCallback = callback
                val root = window.decorView as FrameLayout
                root.addView(view, FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                ))
                webView.visibility = View.GONE
            }

            override fun onHideCustomView() {
                val root = window.decorView as FrameLayout
                root.removeView(customView)
                customView = null
                customViewCallback?.onCustomViewHidden()
                webView.visibility = View.VISIBLE
            }

            // 새 창 열기
            override fun onCreateWindow(
                view: WebView?, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message?
            ): Boolean {
                val newView = WebView(this@MainActivity)
                newView.webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                        webView.loadUrl(request.url.toString())
                        return true
                    }
                }
                (resultMsg?.obj as? WebView.WebViewTransport)?.webView = newView
                resultMsg?.sendToTarget()
                return true
            }

            // console.log 디버깅
            override fun onConsoleMessage(msg: ConsoleMessage?): Boolean {
                return true
            }
        }

        // 다운로드 처리
        webView.setDownloadListener { url, userAgent, contentDisposition, mimeType, contentLength ->
            try {
                val fileName = URLUtil.guessFileName(url, contentDisposition, mimeType)
                val request = DownloadManager.Request(Uri.parse(url)).apply {
                    setMimeType(mimeType)
                    addRequestHeader("User-Agent", userAgent)
                    addRequestHeader("Cookie", CookieManager.getInstance().getCookie(url))
                    setTitle(fileName)
                    setDescription("AEGIS 파일 다운로드")
                    setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                }
                val dm = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                dm.enqueue(request)
                Toast.makeText(this, "다운로드: $fileName", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this, "다운로드 실패", Toast.LENGTH_SHORT).show()
            }
        }

        // 인텐트로 들어온 URL 처리 (딥링크)
        val url = intent?.data?.toString() ?: HOME_URL
        webView.loadUrl(url)
    }

    private fun requestAllPermissions() {
        val perms = mutableListOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        )
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            perms.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            perms.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val needed = perms.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (needed.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, needed.toTypedArray(), PERMISSION_CODE)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == FILE_CHOOSER_CODE) {
            val result = if (resultCode == Activity.RESULT_OK) {
                data?.data?.let { arrayOf(it) }
                    ?: data?.clipData?.let { clip ->
                        Array(clip.itemCount) { clip.getItemAt(it).uri }
                    }
            } else null
            fileCallback?.onReceiveValue(result)
            fileCallback = null
        }
    }

    // 뒤로가기 처리
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (customView != null) {
                webView.webChromeClient?.onHideCustomView()
                return true
            }
            if (webView.canGoBack()) {
                webView.goBack()
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onResume() {
        super.onResume()
        webView.onResume()
    }

    override fun onPause() {
        webView.onPause()
        super.onPause()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        intent?.data?.toString()?.let { webView.loadUrl(it) }
    }
}
