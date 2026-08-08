package com.parlor.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.parlor.networking.transport.RoomTransport
import org.koin.android.ext.android.inject

/**
 * The single Activity. Compose owns navigation; the system bars are drawn
 * edge-to-edge so the cozy-noir backdrop runs from frame to frame.
 */
class MainActivity : ComponentActivity() {
    private val roomTransport: RoomTransport by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            App()
        }
    }

    override fun onStart() {
        super.onStart()
        roomTransport.notifyAppForegrounded()
    }

    override fun onStop() {
        roomTransport.notifyAppBackgrounded()
        super.onStop()
    }
}
