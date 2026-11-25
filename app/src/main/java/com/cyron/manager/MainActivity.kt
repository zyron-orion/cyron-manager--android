package com.cyron.manager

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.cyron.manager.presentation.CyronApp
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val sharedLinks = MutableSharedFlow<String>(replay = 1)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val preferences = getSharedPreferences("cyron_ui", MODE_PRIVATE)
        val systemDark = resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
        val dark = if (preferences.getBoolean("follow_system_theme", false)) systemDark else preferences.getBoolean("dark_theme", true)
        window.setBackgroundDrawable(ColorDrawable(if (dark) Color.rgb(0x0B, 0x0E, 0x16) else Color.rgb(0xF4, 0xF5, 0xFA)))
        if (Build.VERSION.SDK_INT >= 29) window.isNavigationBarContrastEnforced = false
        setContent { CyronApp() }
        handleIntent(intent)
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    fun observeSharedLinks() = sharedLinks.asSharedFlow()

    private fun handleIntent(intent: android.content.Intent?) {
        if (intent?.action != android.content.Intent.ACTION_SEND) return
        if (intent.type?.startsWith("text/plain") != true) return
        val text = intent.getStringExtra(android.content.Intent.EXTRA_TEXT)?.trim().orEmpty()
        if (text.isNotEmpty()) sharedLinks.tryEmit(text)
    }
}
