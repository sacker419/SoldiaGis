package com.example.soldiagis

import android.content.Context
import android.graphics.drawable.Icon
import android.os.Bundle
import android.util.Log
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
import com.naver.maps.map.LocationTrackingMode
import com.naver.maps.map.overlay.OverlayImage.fromResource
import com.naver.maps.map.util.FusedLocationSource
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.Socket

class MainActivity : AppCompatActivity(), OnMapReadyCallback {
    val openIcon = fromResource(R.drawable.open)
    val closeIcon = fromResource(R.drawable.close)
    val waitIcon = fromResource(R.drawable.wait)

    var TAG: String = "로그"

    private lateinit var locationSource: FusedLocationSource
    private lateinit var naverMap: NaverMap

    private val marker = Marker()

    data class Breaker(val breakerId: String, val lng: Double, val lat: Double, val st: String)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 뷰 역할을 하는 프래그먼트 객체 얻기
        val fm = supportFragmentManager
        val mapFragment = fm.findFragmentById(R.id.map_fragment) as MapFragment?
            ?: MapFragment.newInstance().also {
                fm.beginTransaction().add(R.id.map_fragment, it).commit()
            }

        // 인터페이스 역할을 하는 NaverMap 객체 얻기
        // 프래그먼트(MapFragment)의 getMapAsync() 메서드로 OnMapReadyCallback 을 등록하면 비동기로 NaverMap 객체를 얻을 수 있다고 한다.
        // NaverMap 객체가 준비되면 OnMapReady() 콜백 메서드 호출
        mapFragment.getMapAsync(this)

        locationSource = FusedLocationSource(this, LOCATION_PERMISSION_REQUEST_CODE)
    }

    override fun onMapReady(naverMap: NaverMap) {
        this.naverMap = naverMap

        // 초기 좌표와 zoom level 설정
        val cameraPosition = CameraPosition(LatLng(37.4294305, 127.2550476), 12.0)

        // 지도 최소 및 최대 줌 레벨
//        naverMap.minZoom = 16.0
//        naverMap.maxZoom = 18.0

        // 카메라 영역 제한
//        naverMap.extent = LatLngBounds(LatLng(31.43, 122.37), LatLng(44.35, 132.0))

        naverMap.cameraPosition = cameraPosition

        naverMap.locationSource = locationSource
        naverMap.uiSettings.isLocationButtonEnabled = false // 현재 위치 이동

        insertMarker()
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
    companion object {
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1000
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
                    breakerObject.getString("st")
                )
                breakerList.add(breaker)
            }
        } catch (e: JSONException) {
            e.printStackTrace()
        }
        return breakerList
    }

    private fun setMark(marker: Marker, lat: Double, lng: Double, zIndex: Int, breakerId: String, st: String) {
        // 원근감 표시
        marker.setIconPerspectiveEnabled(true)
        // 아이콘 지정
        if (st == "1") {
            marker.setIcon(openIcon)
        } else if (st == "2") {
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
        marker.map = naverMap
        // 마커 offset
        marker.captionOffset = 20
        // 마커 캡션 위치
        marker.setCaptionAligns(Align.Top)
        // 마커 불투명도
        marker.alpha = 1f // 0~1f (0 : 투명, 1 : 불투명)
    }

    private fun insertMarker() {
        // assets 폴더에서 db.json 파일 읽어오기
        try {
            val inputStream = applicationContext.assets.open("db.json")
            val size = inputStream.available()
            val buffer = ByteArray(size)
            inputStream.read(buffer)
            inputStream.close()

            val jsonStr = String(buffer, Charset.forName("UTF-8"))

            // JSON 데이터 파싱
            val breakerList = parseJson(jsonStr)

            // breakerList를 순회하며 각각의 마커 추가
            breakerList.forEach { breaker ->
                // 각각의 마커를 위해 새로운 Marker 객체 생성
                val newMarker = Marker()

                // setMark 함수 내에서 새로운 Marker 객체 사용
                setMark(newMarker, breaker.lat, breaker.lng, 0, breaker.breakerId, breaker.st)

                // 각 마커에 대한 클릭 이벤트 처리
                newMarker.setOnClickListener {
                    showMarkerDialog(newMarker, breaker.breakerId, breaker.lat, breaker.lng, breaker.st)
                    true
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private fun showMarkerDialog(clickedMarker: Marker, breakerId: String, lat: Double, lng: Double, st: String) {
        // 마커를 클릭했을 때 표시할 다이얼로그 구현
        val builder = AlertDialog.Builder(this)
        var dialogMessage = ""
        var toastMessage = ""

        builder.setTitle(breakerId)
        // 현재 클릭된 마커의 아이콘 정보 가져오기
        val currentIcon = clickedMarker.icon

        if (currentIcon == openIcon) {
            dialogMessage = "현재 상태: Open"
        } else if (currentIcon == closeIcon) {
            dialogMessage = "현재 상태: Closed"
        } else {
            dialogMessage = "현재 상태: Wait"
        }
        builder.setMessage(dialogMessage)

        builder.setPositiveButton("Open") { _, _ ->
            Thread {
                val jsonDataComparison = sendJsonDataToServer(breakerId, lat, lng, "1")


                if (jsonDataComparison == true) {
                    runOnUiThread {
                        if (currentIcon != openIcon) {
                            // 클릭된 마커의 아이콘을 변경
                            clickedMarker.setIcon(openIcon)
                            toastMessage = "차단기를 열었습니다."
                        } else {
                            toastMessage = "차단기가 이미 열려있습니다."
                        }
                        showToast("'$breakerId' '$toastMessage'")
                    }
                } else {
                    showToast("Json Data receive error")
                }
            }.start()
        }

        builder.setNegativeButton("Close") { _, _ ->
            Thread {
                val jsonDataComparison = sendJsonDataToServer(breakerId, lat, lng, "1")

                if (jsonDataComparison == true) {
                    sendJsonDataToServer(breakerId, lat, lng, "2")
                    runOnUiThread {
                        if (currentIcon != closeIcon) {
                            // 클릭된 마커의 아이콘을 변경
                            clickedMarker.setIcon(closeIcon)
                            toastMessage = "차단기를 닫았습니다."
                        } else {
                            toastMessage = "차단기가 이미 닫혀있습니다."
                        }
                        showToast("'$breakerId' '$toastMessage'")
                    }
                } else {
                    showToast("Json Data receive error")
                }
            }.start()
        }
        builder.show()
    }

    private fun sendJsonDataToServer(breakerId: String, lat: Double, lng: Double, st: String): Boolean {
        try {
            // 소켓 연결
            val socket = Socket("192.168.30.21", 9001) // 서버의 IP 주소와 포트 번호
            val outputStream: OutputStream = socket.getOutputStream()

            // JSON 형식의 데이터 생성
            val jsonData = """
            {
                "breakerId": "$breakerId",
                "lng": $lng,
                "lat": $lat,
                "st": "$st"
            }
        """.trimIndent()

            // 서버로 JSON 데이터 전송
            outputStream.write(jsonData.toByteArray())

            // 서버로부터 응답 받기
            val bufferedReader = BufferedReader(InputStreamReader(socket.getInputStream()))
            val response = bufferedReader.readLine()

            // 응답 출력
            Log.d(TAG, "Received response from server: $response")

            // 서버로부터 받은 JSON 데이터 다시 파싱
            try {
                val jsonResponse = JSONObject(response)
                val receiveBreakerId = jsonResponse.getString("breakerId")
                if (breakerId == receiveBreakerId) {
                    // showToast("receiveBreakerId = receiveBreakerId")
                    socket.close()
                    return true
                }
            } catch (e: JSONException) {
                e.printStackTrace()
            }
            socket.close()
        } catch (e: IOException) {
            e.printStackTrace()
        }
        return false
    }

    private fun showToast(message: String) {
        runOnUiThread {
            Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show()
        }
    }
}