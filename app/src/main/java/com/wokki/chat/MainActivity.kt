package com.wokki.chat

import android.content.SharedPreferences
import android.os.Bundle
import android.view.KeyEvent
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.constraintlayout.widget.ConstraintLayout
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.AsyncTask
import android.util.Log
import android.webkit.WebResourceRequest
import android.widget.Toast
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import android.content.Intent
import android.net.Uri
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkInfo
import android.webkit.WebResourceError

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var rootLayout: ConstraintLayout
    private var initialLayoutTop: Int = 0
    private val appVersionUrl = "https://levgames.nl/jonazwetsloot/chat/api/app/version.json" // URL to fetch the version info

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        // Initialize SharedPreferences
        sharedPreferences = getSharedPreferences("WebViewPrefs", MODE_PRIVATE)

        webView = findViewById(R.id.webView)
        rootLayout = findViewById(R.id.main)  // Use ConstraintLayout here

        // Enable JavaScript
        val webSettings: WebSettings = webView.settings
        webSettings.javaScriptEnabled = true

        // Enable LocalStorage
        webSettings.domStorageEnabled = true

        // Disable zooming
        webSettings.setSupportZoom(false)
        webSettings.builtInZoomControls = false
        webSettings.displayZoomControls = false

        // Enable wide viewport and set zoomed out to the max
        webSettings.useWideViewPort = true
        webSettings.loadWithOverviewMode = true

        // Set up the WebViewClient to handle links inside the WebView
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)

                // Check for update only after the page has finished loading
                checkForUpdate()

                // Inject JavaScript to hide the androidAppNotif element after the page is loaded
                val jsCode = """
                    var androidAppNotif = document.getElementById('androidAppNotif');
                    if (androidAppNotif) {
                        androidAppNotif.style.display = 'none';
                    }
                """
                // Execute JavaScript to hide the element
                webView.evaluateJavascript(jsCode, null)
            }



            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                // If the URL is an APK link, open it in the browser
                if (url?.endsWith(".apk") == true) {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    startActivity(intent)
                    return true
                }
                return super.shouldOverrideUrlLoading(view, url)
            }
                override fun onReceivedError(
                    view: WebView, request: WebResourceRequest,
                    error: WebResourceError
                ) {
                    super.onReceivedError(view, request, error)

                    // Check if there is an internet connection
                    if (isNetworkAvailable(applicationContext)) {
                        // If internet is available but error still occurs, load a fallback page
                        webView.loadUrl("about:blank")  // Load a blank page or show error page
                    } else {
                        // If there's no internet, load the "no Wi-Fi" page
                        webView.loadUrl("file:///android_asset/nowifi.html")
                    }
                }

        }

// Load the last visited URL from SharedPreferences or a default URL
        val lastVisitedUrl = sharedPreferences.getString("lastVisitedUrl", "https://levgames.nl/jonazwetsloot/chat/api/")

// Load the URL only if it starts with http or https, otherwise load the default URL
        if (lastVisitedUrl != null && (lastVisitedUrl.startsWith("http") || lastVisitedUrl.startsWith("https"))) {
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

        // Ensure textboxes move above the keyboard
        adjustWebViewForKeyboard()
    }


    fun isNetworkAvailable(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val networkInfo: NetworkInfo? = cm.activeNetworkInfo
        return networkInfo != null && networkInfo.isConnected
    }


    private fun checkForUpdate() {
        // Get the current app version
        val currentVersion = getAppVersion()

        // Fetch the version number from the server
        FetchVersionTask().execute(currentVersion)
    }

    private fun getAppVersion(): String {
        val packageInfo: PackageInfo = packageManager.getPackageInfo(packageName, 0)
        return packageInfo.versionName ?: "0.0.0"
    }

    private inner class FetchVersionTask : AsyncTask<String, Void, String>() {

        override fun doInBackground(vararg params: String?): String? {
            try {
                // Make a request to fetch version.json from the server
                val url = URL(appVersionUrl)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connect()

                val inputStream = connection.inputStream
                val response = inputStream.bufferedReader().use { it.readText() }
                val jsonResponse = JSONObject(response)

                // Assuming the version is provided as a string under "version"
                return jsonResponse.optString("version")
            } catch (e: Exception) {
                Log.e("MainActivity", "Error fetching version info", e)
            }
            return null
        }

        override fun onPostExecute(result: String?) {
            super.onPostExecute(result)

            if (result != null && isNewVersionAvailable(result)) {
                // New version available, show an HTML notification in the WebView
                showUpdateNotification()
            }
        }
    }

    private fun isNewVersionAvailable(serverVersion: String): Boolean {
        val currentVersion = getAppVersion()

        // Compare version numbers (this could be done more robustly with a version comparison library)
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

    // Override the back button behavior to support back swipe functionality
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && webView.canGoBack()) {
            webView.goBack() // Go to the last page in the WebView's history
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    // Save the current URL when the activity is paused
    override fun onPause() {
        super.onPause()
        val currentUrl = webView.url
        sharedPreferences.edit().putString("lastVisitedUrl", currentUrl).apply() // Save the current URL to SharedPreferences
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
}
