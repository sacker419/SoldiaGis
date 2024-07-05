package com.soldia.rams

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.content.Intent

class SplashActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        val sharedPreferences = getSharedPreferences("Settings", Context.MODE_PRIVATE)
        val isFirstRun = sharedPreferences.getBoolean("isFirstRun", true)

        if (isFirstRun) {
            // Mark the first run as completed
            with(sharedPreferences.edit()) {
                putBoolean("isFirstRun", false)
                apply()
            }
            // Start SettingsActivity
            startActivity(Intent(this, SettingsActivity::class.java))
        } else {
            // Delay and then start MainActivity
            Handler(Looper.getMainLooper()).postDelayed({
                startActivity(Intent(this, MainActivity::class.java))
                finish()
            }, 1000) // 1 second delay
        }
    }
}