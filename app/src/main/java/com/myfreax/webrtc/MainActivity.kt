package com.myfreax.webrtc

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import android.content.res.AssetManager
import android.util.Log
import java.io.IOException


class MainActivity : AppCompatActivity() {

    companion object {
        const val TAG = "MainActivity"
    }

    private val startHttpServerButton by lazy {
        findViewById<Button>(R.id.start_http_server)
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val intent = Intent(this,SignalingService::class.java)
        assets.open("index.html")
        startHttpServerButton.setOnClickListener {
            startService(intent)
        }
    }
}