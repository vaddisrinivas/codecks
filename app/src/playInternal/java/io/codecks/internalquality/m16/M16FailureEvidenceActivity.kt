package io.codecks.internalquality.m16

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.view.Gravity
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView

/** Screenshot-safe allowlist surface: no repository, clipboard, intent URI, or user text. */
class M16FailureEvidenceActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val profile = intent.getStringExtra("profile_id").orEmpty()
        val code = intent.getStringExtra("failure_code").orEmpty()
        val windowIndex = intent.getIntExtra("window_index", -1)
        require(profile.matches(Regex("avd0[1-4]-p0[1-5]"))) { "invalid_profile" }
        require(code.matches(Regex("[a-z][a-z0-9_]{0,47}"))) { "invalid_failure_code" }
        require(windowIndex in 1..336) { "invalid_window" }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(48, 48, 48, 48)
            setBackgroundColor(Color.BLACK)
        }
        listOf("M16 AUTONOMOUS_PROXY", "Profile $profile", "Failure $code", "Window $windowIndex").forEach { allowed ->
            layout.addView(TextView(this).apply { text = allowed; textSize = 22f; setTextColor(Color.WHITE); gravity = Gravity.CENTER })
        }
        setContentView(layout)
    }
}
