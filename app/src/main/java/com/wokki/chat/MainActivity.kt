package com.wokki.chat

import android.content.ActivityNotFoundException
import android.content.SharedPreferences
import android.os.Bundle
import android.view.KeyEvent
import android.webkit.*
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.constraintlayout.widget.ConstraintLayout
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var rootLayout: ConstraintLayout
    private var initialLayoutTop: Int = 0
    private val appVersionUrl = "https://levgames.nl/jonazwetsloot/chat/api/app/version.json" // URL to fetch the version info

    private val FILECHOOSER_RESULTCODE = 1
    private val REQUEST_SELECT_FILE = 2
    private var mUploadMessage: ValueCallback<Uri>? = null
    private var uploadMessage: ValueCallback<Array<Uri>>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        // Initialize SharedPreferences
        sharedPreferences = getSharedPreferences("WebViewPrefs", MODE_PRIVATE)

        webView = findViewById(R.id.webView)
        rootLayout = findViewById(R.id.main)

        // Enable JavaScript
        val webSettings: WebSettings = webView.settings
        webSettings.javaScriptEnabled = true
        webSettings.domStorageEnabled = true
        webSettings.setSupportZoom(false)
        webSettings.builtInZoomControls = false
        webSettings.displayZoomControls = false
        webSettings.useWideViewPort = true
        webSettings.loadWithOverviewMode = true

        val cookieManager = android.webkit.CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)

        // WebViewClient to control URL loading
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {

                // Handle APK links
                if (url?.endsWith(".apk") == true) {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    startActivity(intent)
                    return true
                }

                // Handle allowed URLs
                val allowedUrlPatterns = listOf(
                    "https://levgames.nl/jonazwetsloot/chat/api",
                    "http://levgames.nl/jonazwetsloot/chat/api",
                    "https://jonazwetsloot.nl/chat/api"
                )
                for (pattern in allowedUrlPatterns) {
                    if (url?.startsWith(pattern) == true) {
                        view?.loadUrl(url)
                        return true
                    }
                }

                // Open other URLs in external browser
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                startActivity(intent)
                return true
            }

            override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
                super.onPageStarted(view, url, favicon)
                // Log page load
            }

            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                checkForUpdate()
                // Hide update notification if exists
                val jsCode = """
                    var androidAppNotif = document.getElementById('androidAppNotif');
                    if (androidAppNotif) {
                        androidAppNotif.style.display = 'none';
                    }
                """
                webView.evaluateJavascript(jsCode, null)
            }

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                super.onReceivedError(view, request, error)
                if (!isNetworkAvailable()) {
                    webView.loadUrl("file:///android_asset/nowifi.html")
                }
            }
        }

        webView.setWebChromeClient(object : WebChromeClient() {
            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                if (filePathCallback != null) {
                    uploadMessage = filePathCallback
                    val intent = fileChooserParams?.createIntent()
                    try {
                        // Check if the intent is null
                        if (intent != null) {
                            startActivityForResult(intent, REQUEST_SELECT_FILE)
                        } else {
                            Toast.makeText(this@MainActivity, "File chooser not supported", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: ActivityNotFoundException) {
                        uploadMessage = null
                        Toast.makeText(this@MainActivity, "File upload not supported", Toast.LENGTH_SHORT).show()
                        return false
                    }
                }
                return true
            }
        })


        // Load the last visited URL or default to a set URL
        val lastVisitedUrl = sharedPreferences.getString("lastVisitedUrl", "https://levgames.nl/jonazwetsloot/chat/api/") ?: ""
        if (lastVisitedUrl.startsWith("http")) {
            webView.loadUrl(lastVisitedUrl)
        } else {
            webView.loadUrl("https://levgames.nl/jonazwetsloot/chat/api/")
        }

        // Adjust for system bars
        ViewCompat.setOnApplyWindowInsetsListener(rootLayout) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
            adjustWebViewForKeyboard()

    }



    private fun isNetworkAvailable(): Boolean {
        val cm = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        val networkInfo = cm.activeNetworkInfo
        return networkInfo?.isConnected == true
    }

    private fun checkForUpdate() {
        lifecycleScope.launch {
            val currentVersion = getAppVersion()
            val serverVersion = fetchServerVersion()
            if (serverVersion != null && isNewVersionAvailable(serverVersion, currentVersion)) {
                showUpdateNotification()
            }
        }
    }

    private suspend fun fetchServerVersion(): String? {
        return withContext(Dispatchers.IO) {
            try {
                val url = URL(appVersionUrl)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connect()
                val inputStream = connection.inputStream
                val response = inputStream.bufferedReader().use { it.readText() }
                val jsonResponse = JSONObject(response)
                jsonResponse.optString("version")
            } catch (e: Exception) {
                Log.e("MainActivity", "Error fetching version info", e)
                null
            }
        }
    }

    private fun getAppVersion(): String {
        val packageInfo = packageManager.getPackageInfo(packageName, 0)
        return packageInfo.versionName ?: "0.0.0"
    }

    private fun isNewVersionAvailable(serverVersion: String, currentVersion: String): Boolean {
        return serverVersion > currentVersion
    }

    private fun showUpdateNotification() {
        val jsCode = """
            var updateMsg = document.getElementById('androidUpdateMSG');
            if (updateMsg) {
                updateMsg.style.display = 'block';
            }
        """
        webView.evaluateJavascript(jsCode, null)
    }

    // Adjust WebView content and move the entire layout upwards when the keyboard is visible
    private fun adjustWebViewForKeyboard() {
        rootLayout.viewTreeObserver.addOnGlobalLayoutListener {
            val r = android.graphics.Rect()
            rootLayout.getWindowVisibleDisplayFrame(r)
            val screenHeight = rootLayout.rootView.height
            val keypadHeight = screenHeight - r.bottom

            // Only adjust layout if the URL starts with "levgames.nl/jonazwetsloot/chat/api"
            if (webView.url?.startsWith("https://levgames.nl/jonazwetsloot/chat/api") == true) {
                // If the keyboard is visible, move the entire layout upwards
                if (keypadHeight > screenHeight * 0.15) {
                    // Save the initial top position of the layout
                    if (initialLayoutTop == 0) {
                        initialLayoutTop = rootLayout.top
                    }

                    val translationY = -keypadHeight.toFloat()  // Move up by the height of the keyboard
                    rootLayout.translationY = translationY  // Move the layout upwards
                } else {
                    // Restore the layout's original position when the keyboard is hidden
                    rootLayout.translationY = 0f
                }
            }
        }
    }
    // Override the back button behavior to support back swipe functionality
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && webView.canGoBack()) {
            webView.goBack()  // Go to the last page in the WebView's history
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    // Save the current URL when the activity is paused
    override fun onPause() {
        super.onPause()
        val currentUrl = webView.url
        sharedPreferences.edit().putString("lastVisitedUrl", currentUrl).apply()  // Save the current URL to SharedPreferences
    }

    // Handle file selection result
    override fun onActivityResult(requestCode: Int, resultCode: Int, intent: Intent?) {
        super.onActivityResult(requestCode, resultCode, intent)

        if (requestCode == REQUEST_SELECT_FILE) {
            if (resultCode == RESULT_OK) {
                var dataArray: Array<Uri>? = null

                // Check if the data is not null and the result contains data
                if (intent != null) {
                    dataArray = if (intent.data != null) {
                        arrayOf(intent.data!!)  // Single file selected
                    } else if (intent.clipData != null) {
                        // Multiple files selected
                        val clipData = intent.clipData
                        val uriList = mutableListOf<Uri>()
                        for (i in 0 until clipData!!.itemCount) {
                            uriList.add(clipData.getItemAt(i).uri)
                        }
                        uriList.toTypedArray()
                    } else {
                        null
                    }
                }

                // If files are selected, call the appropriate callback
                if (dataArray != null) {
                    uploadMessage?.onReceiveValue(dataArray)
                } else {
                    uploadMessage?.onReceiveValue(null)
                }
                uploadMessage = null  // Reset the upload message
            } else {
                uploadMessage?.onReceiveValue(null)
                uploadMessage = null  // Reset the upload message
            }
        }
    }

}
