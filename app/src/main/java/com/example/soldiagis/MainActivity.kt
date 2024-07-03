package com.example.soldiagis

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.sqlite.SQLiteDatabase
import android.graphics.drawable.Icon
import android.media.RingtoneManager
import android.os.Build
import android.os.Build.VERSION_CODES.R
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.soldiagis.R
import com.naver.maps.geometry.LatLng
import com.naver.maps.map.CameraPosition
import com.naver.maps.map.MapFragment
import com.naver.maps.map.NaverMap
import com.naver.maps.map.OnMapReadyCallback
import com.naver.maps.map.overlay.Marker
import com.naver.maps.map.overlay.OverlayImage
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.nio.charset.Charset
import com.naver.maps.map.overlay.Align
import androidx.appcompat.app.AlertDialog
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.gms.tasks.OnCompleteListener
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.google.gson.JsonObject
import com.naver.maps.map.CameraUpdate
import com.naver.maps.map.LocationTrackingMode
import com.naver.maps.map.overlay.OverlayImage.fromResource
import com.naver.maps.map.util.FusedLocationSource
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.lifecycleScope
import kotlin.concurrent.thread

import kotlinx.coroutines.*
import java.net.Socket
import java.util.concurrent.TimeoutException

class MainActivity : AppCompatActivity(), OnMapReadyCallback {
    val openIcon = fromResource(R.drawable.open)
    val closeIcon = fromResource(R.drawable.close)
    val waitIcon = fromResource(R.drawable.wait)

    private val coroutineScope = CoroutineScope(Dispatchers.Main + Job()) // 7/2
    private val markers = mutableMapOf<String, Marker>() // 7/2

    var TAG: String = "로그"

    lateinit var dbHelper: DBHelper
    lateinit var database: SQLiteDatabase

    var serverIP = "192.168.33.32" // flask ip
//    var serverIP = "192.168.30.21" // flask ip
    var serverPORT = 5001 // flask port
    var tcpPORT = 9001

    private lateinit var locationSource: FusedLocationSource
    private lateinit var naverMap: NaverMap

    private lateinit var receiver: BroadcastReceiver // BroadcastReceiver 추가

    data class Breaker(val breakerId: String, val lng: Double, val lat: Double, val st: Int)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 툴바 설정
        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)

        dbHelper = DBHelper(this, "mydb.db", null, 1)
        database = dbHelper.writableDatabase

        // 서버 데이터 가져오기
        val cursor = dbHelper.getServerData()
        if (cursor != null && cursor.moveToFirst()) {
            serverIP = cursor.getString(cursor.getColumnIndex("serverIP"))
            serverPORT = cursor.getInt(cursor.getColumnIndex("serverPORT"))
            tcpPORT = cursor.getInt(cursor.getColumnIndex("tcpPORT"))
        } else {
            Log.e("MainActivity", "Cursor is null or empty")
        }

        FirebaseMessaging.getInstance().token.addOnCompleteListener(OnCompleteListener { task ->
            if (!task.isSuccessful) {
                Log.w(TAG, "Fetching FCM registration token failed", task.exception)
                return@OnCompleteListener
            }

            // Get new FCM registration token
            val token = task.result
            Log.d("FCM token", token)
        })

        receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val breakerId = intent?.getStringExtra("breakerId")
                // 여기서 breakerId를 사용하여 처리
                if (!breakerId.isNullOrEmpty()) {
                    showToast("Received breakerId from FCM: $breakerId")
                    // 받은 breakerId를 사용하여 처리
                }
            }
        }
        LocalBroadcastManager.getInstance(this).registerReceiver(receiver, IntentFilter("BREAKER_NOTIFICATION")) // BroadcastReceiver 등록

        val fm = supportFragmentManager
        val mapFragment = fm.findFragmentById(R.id.map_fragment) as MapFragment?
            ?: MapFragment.newInstance().also {
                fm.beginTransaction().add(R.id.map_fragment, it).commit()
            }

        mapFragment.getMapAsync(this)
        locationSource = FusedLocationSource(this, LOCATION_PERMISSION_REQUEST_CODE)
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_settings -> {
                val intent = Intent(this, SettingsActivity::class.java)
                startActivity(intent)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onMapReady(naverMap: NaverMap) {
        this.naverMap = naverMap
        val cameraPosition = CameraPosition(LatLng(37.199408, 126.8316951), 14.0)
        naverMap.cameraPosition = cameraPosition
        naverMap.locationSource = locationSource
        naverMap.uiSettings.isLocationButtonEnabled = false // 현재 위치 이동

//        fetchDataFromServer()
        startPeriodicFetch()
    }

    private fun startPeriodicFetch() {
        coroutineScope.launch {
            while (isActive) {
                fetchDataFromServer()
                Log.d("DB 불러오기", "DB 불러오기")
                delay(3000) // 10초 대기
            }
        }
    }

    private fun fetchDataFromServer() {
        // OkHttpClient 설정
        val client = OkHttpClient.Builder().build()
        val request = Request.Builder()
            .url("http://$serverIP:$serverPORT/data")  // Flask 서버의 /data 엔드포인트 URL
            .build()

        // 네트워크 요청 실행
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                e.printStackTrace()
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    val responseData = response.body?.string()
                    if (responseData != null) {
                        Log.d("MainActivity", "Received data from server: $responseData")
                        try {
                            // 응답 데이터를 JSONArray로 변환하여 저장
                            val jsonArray = JSONArray(responseData)
                            val breakerList = parseJsonArray(jsonArray)
                            Log.d("jsonArray", "$jsonArray")
                            Log.d("breakerList", "$breakerList")
                            runOnUiThread {
//                                insertMarker(parseJsonArray(jsonArray))
                                insertMarker(breakerList)
                            }
                        } catch (e: JSONException) {
                            e.printStackTrace()
                        }
                    }
                } else {
                    Log.e("MainActivity", "Failed to fetch data")
                }
            }
        })
    }

    private fun parseJsonArray(jsonArray: JSONArray): List<Breaker> {
        val breakerList = mutableListOf<Breaker>()
        try {
            for (i in 0 until jsonArray.length()) {
                val breakerObject = jsonArray.getJSONObject(i)
                val breaker = Breaker(
                    breakerObject.getString("breakerId"),
                    breakerObject.getDouble("lng"),
                    breakerObject.getDouble("lat"),
                    breakerObject.getInt("st")
                )
                breakerList.add(breaker)
            }
        } catch (e: JSONException) {
            e.printStackTrace()
        }
        return breakerList
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        Log.d(TAG, "MainActivity - onRequestPermissionsResult")
        if (locationSource.onRequestPermissionsResult(
                requestCode,
                permissions,
                grantResults
            )
        ) {
            if (!locationSource.isActivated) { // 권한 거부됨
                Log.d(TAG, "MainActivity - onRequestPermissionsResult 권한 거부됨")
                naverMap.locationTrackingMode = LocationTrackingMode.None
            } else {
                Log.d(TAG, "MainActivity - onRequestPermissionsResult 권한 승인됨")
                naverMap.locationTrackingMode = LocationTrackingMode.Follow // 현위치 버튼 컨트롤 활성
            }
            return
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    private fun parseJson(jsonStr: String): List<Breaker> {
        val breakerList = mutableListOf<Breaker>()
        try {
            val jsonObject = JSONObject(jsonStr)
            val breakerArray = jsonObject.getJSONArray("breaker")
            for (i in 0 until breakerArray.length()) {
                val breakerObject = breakerArray.getJSONObject(i)
                val breaker = Breaker(
                    breakerObject.getString("breakerId"),
                    breakerObject.getDouble("lng"),
                    breakerObject.getDouble("lat"),
                    breakerObject.getInt("st")
                )
                breakerList.add(breaker)
            }
        } catch (e: JSONException) {
            e.printStackTrace()
        }
        return breakerList
    }

    private fun setMark(marker: Marker, lat: Double, lng: Double, zIndex: Int, breakerId: String, st: Int) {
        // 원근감 표시
        marker.setIconPerspectiveEnabled(true)
        // 아이콘 지정
        if (st == 1) {
            marker.setIcon(openIcon)
        } else if (st == 2) {
            marker.setIcon(closeIcon)
        } else {
            marker.setIcon(waitIcon)
        }
        // 마커 위치
        marker.position = LatLng(lat, lng)
        // 마커 우선순위
        marker.zIndex = zIndex
        // 마커 텍스트
        marker.captionText = breakerId
        // 마커 우선순위
        marker.zIndex = 10
        // 마커 표시
//        marker.map = naverMap  // 7.2
        // 마커 offset
        marker.captionOffset = 20
        // 마커 캡션 위치
        marker.setCaptionAligns(Align.Top)
        // 마커 불투명도
        marker.alpha = 1f // 0~1f (0 : 투명, 1 : 불투명)
    }

    private fun insertMarker(breakerList: List<Breaker>) {
        val ClassFCM = MyFirebaseMessagingService() // MyFirebaseMessagingService 인스턴스 생성
        val notibreakerId = ClassFCM.breakerId // breakerId 값을 가져옴

        Log.d("Main", "breakerId : ${notibreakerId.toString()}")
//        showToast(notibreakerId.toString())

        // 새로운 breakerId 집합 생성
        val newBreakerIds = breakerList.map { it.breakerId }.toSet()

        // 제거해야 할 마커 찾기 및 제거
        val markersToRemove = markers.keys - newBreakerIds
        markersToRemove.forEach { id ->
            markers[id]?.map = null  // 지도에서 마커 제거
            markers.remove(id)  // 마커 맵에서 제거
        }

        // breakerList를 순회하며 각각의 마커 추가
//        breakerList.forEach { breaker ->
//            // 각각의 마커를 위해 새로운 Marker 객체 생성
//            val newMarker = Marker()
//            // setMark 함수 내에서 새로운 Marker 객체 사용
//            setMark(newMarker, breaker.lat, breaker.lng, 0, breaker.breakerId, breaker.st)
//            // 각 마커에 대한 클릭 이벤트 처리
//            newMarker.setOnClickListener {
//                showMarkerDialog(newMarker, breaker.breakerId, breaker.lat, breaker.lng, breaker.st)
//                true
//            }
//
//            if (notibreakerId == breaker.breakerId) {
//                val cameraUpdate = CameraUpdate.scrollTo(LatLng(breaker.lat, breaker.lng))
//                naverMap.moveCamera(cameraUpdate)
//            }
//        }
        breakerList.forEach { breaker ->
            val existingMarker = markers[breaker.breakerId]
            if (existingMarker != null) {
                // 기존 마커 업데이트
                updateMarker(existingMarker, breaker)
            } else {
                // 새 마커 생성
                val newMarker = Marker()
                setMark(newMarker, breaker.lat, breaker.lng, 0, breaker.breakerId, breaker.st)
                newMarker.setOnClickListener {
                    showMarkerDialog(newMarker, breaker.breakerId, breaker.st)
                    true
                }
                markers[breaker.breakerId] = newMarker
            }

            if (notibreakerId == breaker.breakerId) {
                val cameraUpdate = CameraUpdate.scrollTo(LatLng(breaker.lat, breaker.lng))
                naverMap.moveCamera(cameraUpdate)
            }
        }
        // 모든 마커를 지도에 표시
        markers.values.forEach { it.map = naverMap }
    }

    private fun updateMarker(marker: Marker, breaker: Breaker) {
        marker.position = LatLng(breaker.lat, breaker.lng)
        when (breaker.st) {
            1 -> marker.icon = openIcon
            2 -> marker.icon = closeIcon
            else -> marker.icon = waitIcon
        }
        // 필요한 경우 다른 마커 속성도 여기서 업데이트
    }

    private fun showMarkerDialog(clickedMarker: Marker, breakerId: String, st: Int) {
        val builder = AlertDialog.Builder(this)
        var dialogMessage = ""
        var toastMessage = ""

        builder.setTitle(breakerId)
        val currentIcon = clickedMarker.icon

        dialogMessage = when (currentIcon) {
            openIcon -> "현재 상태: Open"
            closeIcon -> "현재 상태: Closed"
            else -> "현재 상태: Wait"
        }

        builder.setMessage(dialogMessage)

        builder.setPositiveButton("Open") { _, _ ->
            lifecycleScope.launch {
                try {
                    Log.d("open button click", "open button click")
                    statusComparison(breakerId, 1) { result ->
                        when (result) {
                            "difference" -> {
                                lifecycleScope.launch {
                                    sendJsonDataToServer(breakerId, 1)
                                    delay(1000)
                                    clickedMarker.setIcon(openIcon)
                                    Log.d("차단기 제어", "차단기 open")
                                    toastMessage = "차단기를 열었습니다."
                                    showToast("'$breakerId' $toastMessage")
                                }
                            }
                            "sameness" -> {
                                lifecycleScope.launch {
                                    sendJsonDataToServer(breakerId, 1)
                                    toastMessage = "차단기가 이미 열려있습니다."
                                    showToast("'$breakerId' $toastMessage")
                                }
                            }
                            "not found" -> {
                                showToast("해당 breaker를 찾을 수 없습니다.")
                            }
                            "error" -> {
                                showToast("에러가 발생했습니다.")
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("Open click", "Error: ${e.message}", e)
                    showToast("Error: ${e.message}")
                }
            }
        }

        builder.setNegativeButton("Close") { _, _ ->
            lifecycleScope.launch {
                try {
                    Log.d("close button click", "close button click")
                    statusComparison(breakerId, 2) { result ->
                        when (result) {
                            "difference" -> {
                                lifecycleScope.launch {
                                    sendJsonDataToServer(breakerId, 2)
                                    delay(1000)
                                    clickedMarker.setIcon(closeIcon)
                                    Log.d("차단기 제어", "차단기 close")
                                    toastMessage = "차단기를 닫았습니다."
                                    showToast("'$breakerId' $toastMessage")
                                }
                            }
                            "sameness" -> {
                                lifecycleScope.launch {
                                    sendJsonDataToServer(breakerId, 2)
                                    toastMessage = "차단기가 이미 닫혀있습니다."
                                    showToast("'$breakerId' $toastMessage")
                                }
                            }
                            "not found" -> {
                                showToast("해당 breaker를 찾을 수 없습니다.")
                            }
                            "error" -> {
                                showToast("에러가 발생했습니다.")
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("Close click", "Error: ${e.message}", e)
                    showToast("Error: ${e.message}")
                }
            }
        }
        builder.show()
    }

    private fun statusComparison(breakerId: String, st: Int, callback: (String) -> Unit) {
        val client = OkHttpClient.Builder().build()
        val request = Request.Builder()
            .url("http://$serverIP:$serverPORT/data")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                e.printStackTrace()
                callback("error")  // 에러 발생 시 "error" 반환
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    val responseData = response.body?.string()
                    if (responseData != null) {
                        try {
                            val jsonArray = JSONArray(responseData)
                            val breakerList = parseJsonArray(jsonArray)

                            val targetBreaker = breakerList.find { it.breakerId == breakerId }
                            if (targetBreaker != null) {
                                val result = if (st == targetBreaker.st) {
                                    "sameness"
                                } else {
                                    "difference"
                                }
                                callback(result)  // 결과 반환
                            } else {
                                callback("not found")  // breaker를 찾지 못한 경우
                            }
                        } catch (e: JSONException) {
                            e.printStackTrace()
                            callback("error")  // JSON 파싱 에러 시 "error" 반환
                        }
                    } else {
                        callback("error")  // 응답 데이터가 null인 경우 "error" 반환
                    }
                } else {
                    callback("error")  // 응답이 실패한 경우 "error" 반환
                }
            }
        })
    }
//    private fun updateServerData(breakerId: String, st: Int) {
//        // OkHttpClient 설정
//        val client = OkHttpClient.Builder().build()
//
//        // POST 요청 Body 데이터 설정
//        val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
//        val json = JSONObject()
//        json.put("breakerId", breakerId)
//        json.put("st", st)
//        json.put("mobile", 1)
//        val requestBody = RequestBody.create(mediaType, json.toString())
//
//        // POST 요청 생성
//        val request = Request.Builder()
//            .url("http://$serverIP:$serverPORT/update")
//            .post(requestBody)
//            .build()
//
//        // 네트워크 요청 실행
//        client.newCall(request).enqueue(object : Callback {
//            override fun onFailure(call: Call, e: IOException) {
//                e.printStackTrace()
//            }
//
//            override fun onResponse(call: Call, response: Response) {
//                response.use {
//                    if (!response.isSuccessful) {
//                        Log.e(TAG, "Failed to update data")
//                        return
//                    }
//                    Log.d(TAG, "Data updated successfully")
//                    // 원하는 경우 응답을 처리할 수 있음
//                }
//            }
//        })
//    }

    // 반환할 데이터를 위한 데이터 클래스
    data class SentData(val breakerId: String, val st: Int, val mobile: Int)

    private suspend fun sendJsonDataToServer(breakerId: String, st: Int): SentData = withContext(Dispatchers.IO) {
        Log.d("sendJsonDataToServer", "sendJsonDataToServer 실행됨")
        val mobile = 1
        val jsonData = """
        {
            "breakerId": "$breakerId",
            "st": $st,
            "mobile": $mobile
        }
        """.trimIndent()

        try {
            Socket(serverIP, tcpPORT).use { socket ->
                val outputStream: OutputStream = socket.getOutputStream()
                outputStream.write(jsonData.toByteArray())
            }
            SentData(breakerId, st, mobile)
        } catch (e: IOException) {
            Log.e(TAG, "Error sending data to server", e)
            throw e
        }
    }

    override fun onDestroy() {
        coroutineScope.cancel()// 7/2
        LocalBroadcastManager.getInstance(this).unregisterReceiver(receiver)
        super.onDestroy()
    }

    private fun showToast(message: String) {
        runOnUiThread {
            Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1000
    }
}

class MyFirebaseMessagingService : FirebaseMessagingService() {

    private val TAG = "FCM Log"
    var breakerId: String? = null

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG,"Refresh token: $token")
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Log.d(TAG, "From: ${remoteMessage.from}")

        remoteMessage.data.isNotEmpty().let {
            Log.d(TAG, "Message data payload: " + remoteMessage.data)
            val data = remoteMessage.data

            val breakerId = data["breakerId"]
            if (!breakerId.isNullOrEmpty()) {
                Log.d(TAG, "Received breakerId: $breakerId")
                val intent = Intent("BREAKER_NOTIFICATION")
                intent.putExtra("breakerId", breakerId)
                LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
            } else {
                Log.d(TAG, "breakerId is empty or null")
            }
        }

        remoteMessage.notification?.let {
            val notifiBreakerId = it.body
            Log.d(TAG, "Message Notification Body: ${notifiBreakerId}")
        }
    }
}
