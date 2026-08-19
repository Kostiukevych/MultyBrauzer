package com.example.minibrowser8

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.LinearLayout

/**
 * A single browser "slot": holds its WebView plus the small UI wrapped around it
 * (url bar, navigation buttons, fullscreen toggle) and remembers where it lives
 * in the grid so it can be put back after being shown fullscreen.
 */
class BrowserSlot(val index: Int) {
    lateinit var card: LinearLayout
    lateinit var webView: WebView
    lateinit var urlInput: EditText
    lateinit var fullscreenButton: Button
}

class MainActivity : Activity() {

    private val slots = mutableListOf<BrowserSlot>()
    private var fullscreenSlot: BrowserSlot? = null

    private lateinit var rootFrame: FrameLayout
    private lateinit var mainContent: LinearLayout
    private lateinit var fullscreenContainer: FrameLayout
    private lateinit var grid: GridLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        CookieManager.getInstance().setAcceptCookie(true)

        buildUi()
    }

    // ---------------------------------------------------------------------
    // UI construction (built entirely in code, no XML layout needed)
    // ---------------------------------------------------------------------

    @SuppressLint("SetJavaScriptEnabled")
    private fun buildUi() {
        rootFrame = FrameLayout(this).apply {
            setBackgroundColor(Color.rgb(8, 8, 8))
        }

        mainContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }

        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(20, 10, 20, 10)
            setBackgroundColor(Color.rgb(21, 21, 21))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(48)
            )
        }
        val title = android.widget.TextView(this).apply {
            text = "8 Mini Browsers"
            setTextColor(Color.WHITE)
            textSize = 14f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
            gravity = Gravity.CENTER_VERTICAL
        }
        topBar.addView(title)
        mainContent.addView(topBar)

        grid = GridLayout(this).apply {
            columnCount = 2
            rowCount = 4
            setPadding(dp(2), dp(2), dp(2), dp(2))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }
        mainContent.addView(grid)

        for (i in 0 until 8) {
            val slot = BrowserSlot(i)
            slot.card = createCard(slot)
            slots.add(slot)
            addCardToGrid(slot)
        }

        rootFrame.addView(mainContent)

        fullscreenContainer = FrameLayout(this).apply {
            visibility = View.GONE
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.BLACK)
        }
        rootFrame.addView(fullscreenContainer)

        setContentView(rootFrame)
    }

    private fun addCardToGrid(slot: BrowserSlot) {
        val params = GridLayout.LayoutParams().apply {
            width = 0
            height = 0
            rowSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
            columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
            setMargins(4, 4, 4, 4)
        }
        grid.addView(slot.card, slot.index, params)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createCard(slot: BrowserSlot): LinearLayout {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(20, 20, 20))
        }

        // --- Row 1: editable URL bar + Go button ---
        val urlRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(2), dp(4), dp(2))
            setBackgroundColor(Color.rgb(30, 30, 30))
        }

        val urlInput = EditText(this).apply {
            hint = "Введите ссылку…"
            setHintTextColor(Color.rgb(130, 130, 130))
            setTextColor(Color.WHITE)
            textSize = 11f
            setSingleLine(true)
            inputType = InputType.TYPE_TEXT_VARIATION_URI
            imeOptions = EditorInfo.IME_ACTION_GO
            setBackgroundColor(Color.rgb(45, 45, 45))
            setPadding(dp(6), dp(2), dp(6), dp(2))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        slot.urlInput = urlInput

        val goButton = smallButton("Go") {
            loadUrlFromInput(slot)
        }
        urlInput.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_GO ||
                (event != null && event.keyCode == KeyEvent.KEYCODE_ENTER)
            ) {
                loadUrlFromInput(slot)
                true
            } else {
                false
            }
        }

        urlRow.addView(urlInput)
        urlRow.addView(goButton)
        card.addView(
            urlRow,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(32))
        )

        // --- Row 2: navigation + fullscreen controls ---
        val controlRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.rgb(35, 35, 35))
        }

        controlRow.addView(smallButton("‹") { slot.webView.let { if (it.canGoBack()) it.goBack() } })
        controlRow.addView(smallButton("›") { slot.webView.let { if (it.canGoForward()) it.goForward() } })
        controlRow.addView(smallButton("↻") { slot.webView.reload() })

        val fullscreenButton = smallButton("⛶") { toggleFullscreen(slot) }
        slot.fullscreenButton = fullscreenButton
        controlRow.addView(fullscreenButton)

        card.addView(
            controlRow,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(30))
        )

        // --- WebView ---
        val webView = WebView(this).apply {
            configureWebView(this)
        }
        webView.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            0,
            1f
        )
        slot.webView = webView
        card.addView(webView)

        return card
    }

    private fun smallButton(label: String, onClick: () -> Unit): Button {
        return Button(this).apply {
            text = label
            textSize = 11f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.TRANSPARENT)
            minWidth = dp(36)
            minimumWidth = dp(36)
            minHeight = dp(28)
            minimumHeight = dp(28)
            setPadding(0, 0, 0, 0)
            setOnClickListener { onClick() }
        }
    }

    private fun loadUrlFromInput(slot: BrowserSlot) {
        var text = slot.urlInput.text.toString().trim()
        if (text.isEmpty()) return
        if (!text.startsWith("http://") && !text.startsWith("https://")) {
            text = "https://$text"
        }
        slot.urlInput.setText(text)
        slot.webView.loadUrl(text)
        // Hide keyboard after submitting.
        currentFocus?.let {
            val imm = getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            imm.hideSoftInputFromWindow(it.windowToken, 0)
        }
    }

    // ---------------------------------------------------------------------
    // Fullscreen toggle
    // ---------------------------------------------------------------------

    private fun toggleFullscreen(slot: BrowserSlot) {
        if (fullscreenSlot == slot) {
            exitFullscreen()
        } else if (fullscreenSlot == null) {
            enterFullscreen(slot)
        }
        // If a different slot is fullscreen, ignore taps on other cards' buttons
        // (they're hidden anyway while another slot is fullscreen).
    }

    private fun enterFullscreen(slot: BrowserSlot) {
        grid.removeView(slot.card)
        fullscreenContainer.addView(
            slot.card,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        mainContent.visibility = View.GONE
        fullscreenContainer.visibility = View.VISIBLE
        slot.fullscreenButton.text = "🗗"
        fullscreenSlot = slot
    }

    private fun exitFullscreen() {
        val slot = fullscreenSlot ?: return
        fullscreenContainer.removeView(slot.card)
        addCardToGrid(slot)
        fullscreenContainer.visibility = View.GONE
        mainContent.visibility = View.VISIBLE
        slot.fullscreenButton.text = "⛶"
        fullscreenSlot = null
    }

    // ---------------------------------------------------------------------
    // WebView configuration
    // ---------------------------------------------------------------------

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureWebView(webView: WebView) {
        val settings = webView.settings

        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true

        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)

        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true

        settings.setSupportZoom(true)
        settings.builtInZoomControls = false
        settings.displayZoomControls = false

        settings.mediaPlaybackRequiresUserGesture = false
        settings.cacheMode = WebSettings.LOAD_DEFAULT
        settings.setGeolocationEnabled(true)

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest
            ): Boolean = false
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onGeolocationPermissionsShowPrompt(
                origin: String?,
                callback: GeolocationPermissions.Callback?
            ) {
                callback?.invoke(origin, true, false)
            }
        }

        settings.userAgentString = WebSettings.getDefaultUserAgent(this)

        webView.isVerticalScrollBarEnabled = true
        webView.isHorizontalScrollBarEnabled = false
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null)
    }

    private fun dp(value: Int): Int {
        val density = resources.displayMetrics.density
        return (value * density).toInt()
    }

    // ---------------------------------------------------------------------
    // Back button handling
    // ---------------------------------------------------------------------

    override fun onBackPressed() {
        val fs = fullscreenSlot
        if (fs != null) {
            if (fs.webView.canGoBack()) {
                fs.webView.goBack()
            } else {
                exitFullscreen()
            }
            return
        }
        for (slot in slots.reversed()) {
            if (slot.webView.canGoBack()) {
                slot.webView.goBack()
                return
            }
        }
        super.onBackPressed()
    }

    override fun onDestroy() {
        for (slot in slots) {
            slot.webView.stopLoading()
            slot.webView.loadUrl("about:blank")
            slot.webView.clearHistory()
            slot.webView.destroy()
        }
        slots.clear()
        super.onDestroy()
    }
}
