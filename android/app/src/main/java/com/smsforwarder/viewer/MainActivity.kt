package com.smsforwarder.viewer

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

/**
 * Placeholder screen. Login, device list and message feed are added
 * in later milestones once the backend REST API is available.
 */
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
    }
}
