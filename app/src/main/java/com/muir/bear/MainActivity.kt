package com.muir.bear

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.muir.bear.steps.Steps
import com.muir.bear.timer.RestTimer
import kotlinx.coroutines.flow.MutableStateFlow
import com.muir.bear.ui.AppRoot
import com.muir.bear.ui.theme.BearTheme

class MainActivity : ComponentActivity() {
    private val permissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            Steps.startListening(this) // starts counting if "Physical activity" was allowed
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val wanted = buildList {
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
            add(Manifest.permission.ACTIVITY_RECOGNITION) // pedometer
        }.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (wanted.isNotEmpty()) permissions.launch(wanted.toTypedArray())
        handleOpen(intent)
        setContent {
            BearTheme {
                AppRoot()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleOpen(intent)
    }

    private fun handleOpen(intent: Intent?) {
        if (intent?.getStringExtra(EXTRA_OPEN) == "sleep") openRequest.value = "sleep"
    }

    override fun onResume() {
        super.onResume()
        RestTimer.appVisible = true
        Steps.startListening(this)
    }

    override fun onPause() {
        RestTimer.appVisible = false
        Steps.stopListening(this)
        super.onPause()
    }

    companion object {
        const val EXTRA_OPEN = "open"

        /** A screen the app was asked to open (e.g. from the alarm notification). */
        val openRequest = MutableStateFlow<String?>(null)
    }
}
