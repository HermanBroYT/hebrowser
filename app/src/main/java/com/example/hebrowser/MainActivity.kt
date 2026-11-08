package com.example.hebrowser

import android.os.Bundle
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var urlEditText: EditText
    private lateinit var progressBar: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Инициализация представлений
        webView = findViewById(R.id.webView)
        urlEditText = findViewById(R.id.urlEditText)
        progressBar = findViewById(R.id.progressBar)

        val btnBack: Button = findViewById(R.id.btnBack)
        val btnForward: Button = findViewById(R.id.btnForward)
        val btnRefresh: Button = findViewById(R.id.btnRefresh)
        val btnGo: Button = findViewById(R.id.btnGo)

        // Настройка WebView
        val webSettings = webView.settings
        webSettings.javaScriptEnabled = true
        webSettings.domStorageEnabled = true
        webSettings.builtinZoomControls = true
        webSettings.displayZoomControls = false
        webSettings.loadWithOverviewMode = true
        webSettings.useWideViewPort = true

        // Загрузка страниц внутри приложения (не во внешнем браузере)
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                view.loadUrl(url)
                return true
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                progressBar.visibility = android.view.View.GONE
                urlEditText.setText(url)
                supportActionBar?.title = view?.title
            }

            override fun onReceivedError(view: WebView?, requestId: Int?, error: android.webkit.WebResourceError?) {
                super.onReceivedError(view, requestId, error)
                progressBar.visibility = android.view.View.GONE
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                super.onProgressChanged(view, newProgress)
                progressBar.progress = newProgress
                if (newProgress == 100) {
                    progressBar.visibility = android.view.View.GONE
                } else {
                    progressBar.visibility = android.view.View.VISIBLE
                }
            }

            override fun onReceivedTitle(view: WebView?, title: String?) {
                super.onReceivedTitle(view, title)
                supportActionBar?.title = title
            }
        }

        // Кнопка «Назад»
        btnBack.setOnClickListener {
            if (webView.canGoBack()) {
                webView.goBack()
            }
        }

        // Кнопка «Вперёд»
        btnForward.setOnClickListener {
            if (webView.canGoForward()) {
                webView.goForward()
            }
        }

        // Кнопка «Обновить»
        btnRefresh.setOnClickListener {
            webView.reload()
        }

        // Кнопка «Перейти»
        btnGo.setOnClickListener {
            val url = urlEditText.text.toString().trim()
            if (url.isNotEmpty()) {
                loadUrl(url)
            }
        }

        // Обработка нажатия клавиши «Ввод» на клавиатуре
        urlEditText.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO) {
                val url = urlEditText.text.toString().trim()
                if (url.isNotEmpty()) {
                    loadUrl(url)
                }
                true
            } else {
                false
            }
        }

        // Стартовая страница
        loadUrl("https://www.google.com")
    }

    private fun loadUrl(url: String) {
        val formattedUrl = if (!url.startsWith("http://") && !url.startsWith("https://")) {
            "https://$url"
        } else {
            url
        }
        webView.loadUrl(formattedUrl)
        urlEditText.setText(formattedUrl)
        progressBar.visibility = android.view.View.VISIBLE
    }

    // Кнопка «Назад» на устройстве — сначала назад в истории браузера
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && webView.canGoBack()) {
            webView.goBack()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onDestroy() {
        webView.destroy()
        super.onDestroy()
    }
}
