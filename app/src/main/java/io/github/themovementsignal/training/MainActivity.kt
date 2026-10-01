package io.github.themovementsignal.training

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import io.github.themovementsignal.training.timer.RestTimer
import io.github.themovementsignal.training.ui.AppRoot
import io.github.themovementsignal.training.ui.theme.TrainingTheme

class MainActivity : ComponentActivity() {
    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* the timer still works in-app if denied */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent {
            TrainingTheme {
                AppRoot()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        RestTimer.appVisible = true
    }

    override fun onPause() {
        RestTimer.appVisible = false
        super.onPause()
    }
}
