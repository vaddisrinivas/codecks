package io.codecks.internalquality

import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import androidx.activity.ComponentActivity

/** Internal-only lifecycle/input probe. It is absent from public OSS and Play artifacts. */
class M11DexProxyActivity : ComponentActivity() {
    private val preferences by lazy { getSharedPreferences(PREFERENCES, MODE_PRIVATE) }
    lateinit var probeView: View
        private set
    var pointerEvents: Int = 0
        private set
    var keyboardEvents: Int = 0
        private set
    var windowMarker: Int = 0
        private set
    var durableMarker: Int = 0
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        windowMarker = savedInstanceState?.getInt(WINDOW_MARKER) ?: 0
        durableMarker = preferences.getInt(DURABLE_MARKER, 0)
        probeView = object : View(this) {
            override fun onTouchEvent(event: MotionEvent): Boolean {
                if (event.actionMasked == MotionEvent.ACTION_UP && event.isFromSource(android.view.InputDevice.SOURCE_MOUSE)) {
                    recordPointer()
                }
                return true
            }
        }.apply {
            contentDescription = "M11 DeX proxy input target"
            isFocusable = true
            isFocusableInTouchMode = true
            requestFocus()
        }
        setContentView(probeView)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt(WINDOW_MARKER, windowMarker)
        super.onSaveInstanceState(outState)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_UP && probeView.hasFocus()) {
            keyboardEvents += 1
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    fun resetDurableMarker() {
        durableMarker = 0
        preferences.edit().remove(DURABLE_MARKER).commit()
    }

    private fun recordPointer() {
        pointerEvents += 1
        windowMarker += 1
        durableMarker += 1
        preferences.edit().putInt(DURABLE_MARKER, durableMarker).commit()
    }

    companion object {
        private const val PREFERENCES = "m11_dex_proxy"
        private const val WINDOW_MARKER = "window_marker"
        private const val DURABLE_MARKER = "durable_marker"
    }
}
