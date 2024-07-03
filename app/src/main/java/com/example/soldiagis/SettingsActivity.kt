package com.example.soldiagis

import android.content.ContentValues
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {

    lateinit var dbHelper: DBHelper
    lateinit var database: SQLiteDatabase

    private lateinit var ipEditText: EditText
    private lateinit var portEditText: EditText
    private lateinit var tcpPortEditText: EditText
    private lateinit var saveButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        dbHelper = DBHelper(this, "mydb.db", null, 1)
        database = dbHelper.writableDatabase

        ipEditText = findViewById(R.id.ip_edit_text)
        portEditText = findViewById(R.id.port_edit_text)
        tcpPortEditText = findViewById(R.id.tcp_port_edit_text)
        saveButton = findViewById(R.id.save_button)

        // 데이터베이스에서 초기값을 가져와서 설정
        loadSettings()

        // EditText 입력 감지
        ipEditText.addTextChangedListener(textWatcher)
        portEditText.addTextChangedListener(textWatcher)
        tcpPortEditText.addTextChangedListener(textWatcher)

        // 초기 상태에서 저장 버튼 비활성화
        saveButton.isEnabled = false
    }

    private fun loadSettings() {
        val cursor = database.query(
            "server",
            arrayOf("serverIP", "serverPORT", "tcpPORT"),
            "id = ?",
            arrayOf("1"),
            null,
            null,
            null
        )

        if (cursor.moveToFirst()) {
            val serverIP = cursor.getString(cursor.getColumnIndexOrThrow("serverIP"))
            val serverPORT = cursor.getInt(cursor.getColumnIndexOrThrow("serverPORT"))
            val tcpPORT = cursor.getInt(cursor.getColumnIndexOrThrow("tcpPORT"))

            ipEditText.setText(serverIP)
            portEditText.setText(serverPORT.toString())
            tcpPortEditText.setText(tcpPORT.toString())
        }
        cursor.close()
    }

    private val textWatcher = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
            // No implementation needed
        }

        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
            // No implementation needed
        }

        override fun afterTextChanged(s: Editable?) {
            // 모든 EditText의 입력 여부 확인
            val ipText = ipEditText.text.toString().trim()
            val portText = portEditText.text.toString().trim()
            val tcpPortText = tcpPortEditText.text.toString().trim()

            // 모든 입력란이 채워져 있으면 저장 버튼 활성화
            saveButton.isEnabled = ipText.isNotEmpty() && portText.isNotEmpty() && tcpPortText.isNotEmpty()
        }
    }

    fun onSaveButtonClick(view: android.view.View) {
        // EditText에서 입력된 텍스트 가져오기
        val ipText = ipEditText.text.toString().trim()
        val portText = portEditText.text.toString().trim()
        val tcpPortText = tcpPortEditText.text.toString().trim()

        // 입력된 설정값 저장
        saveSettings(ipText, portText, tcpPortText)

        // MainActivity로 화면 전환
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
    }

    private fun saveSettings(serverIP: String, serverPORT: String, tcpPORT: String) {
        val contentValues = ContentValues().apply {
            put("serverIP", serverIP)
            put("serverPORT", serverPORT.toInt())
            put("tcpPORT", tcpPORT.toInt())
        }

        val rowsAffected = database.update(
            "server",
            contentValues,
            "id = ?",
            arrayOf("1")
        )

        if (rowsAffected > 0) {
            showToast("저장되었습니다.")
            Log.d("setting", "저장성공")
        } else {
            Log.d("setting", "저장실패")
            showToast("저장 실패.")
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
