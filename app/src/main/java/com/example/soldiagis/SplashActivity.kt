package com.example.soldiagis

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.content.Intent

//class SplashActivity : AppCompatActivity() {
//    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState)
//        setContentView(R.layout.activity_splash)
//
//        // 일정 시간 지연 이후 실행하기 위한 코드
//        Handler(Looper.getMainLooper()).postDelayed({
//
//            // 일정 시간이 지나면 MainActivity로 이동
//            val intent= Intent( this,MainActivity::class.java)
//            startActivity(intent)
//
//            // 이전 키를 눌렀을 때 스플래스 스크린 화면으로 이동을 방지하기 위해
//            // 이동한 다음 사용안함으로 finish 처리
//            finish()
//
//        }, 1000) // 시간 1초 이후 실행
//    }
//}
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