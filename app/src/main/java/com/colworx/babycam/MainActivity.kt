package com.colworx.babycam

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import com.colworx.babycam.ui.AppNavigation
import com.colworx.babycam.ui.theme.BabyCamTheme

/**
 * Hosts the Compose UI. Extends [FragmentActivity] (not ComponentActivity) so the
 * biometric prompt in AppLockScreen — which resolves `context as? FragmentActivity` —
 * works correctly.
 *
 * Declared `singleTop` in the manifest so the cry-alert deep link reuses this instance;
 * [onNewIntent] re-delivers the "open_parent_live" flag into the running Compose tree.
 */
class MainActivity : FragmentActivity() {

    /** Drives navigation to the parent live screen when launched from a cry alert. */
    private var openParentLive by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        openParentLive = intent?.getBooleanExtra(EXTRA_OPEN_PARENT_LIVE, false) ?: false
        setContent {
            BabyCamTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation(startAtParentLive = openParentLive)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(EXTRA_OPEN_PARENT_LIVE, false)) {
            openParentLive = true
        }
    }

    private companion object {
        /** Shared deep-link extra; mirrored by AGENT-CONN's CryNotifier. */
        const val EXTRA_OPEN_PARENT_LIVE = "open_parent_live"
    }
}
