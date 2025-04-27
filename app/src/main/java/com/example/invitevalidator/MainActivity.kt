package com.example.invitevalidator

import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.budiyev.android.codescanner.*
import okhttp3.*
import org.json.JSONObject
import java.io.IOException

private const val CAMERA_REQUEST_CODE = 101

class MainActivity : AppCompatActivity() {
    private lateinit var codeScanner: CodeScanner

    // 🔊 Tones for feedback
    private val toneGenScan = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
    private val toneGenAccept = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
    private val toneGenReject = ToneGenerator(AudioManager.STREAM_MUSIC, 100)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        supportActionBar?.setLogo(R.drawable.ic_logo)
        supportActionBar?.setDisplayUseLogoEnabled(true)
        supportActionBar?.title = " UV'25 Pass Validator"
        setupPermissions()
        setupCodeScanner()
    }

    private fun setupCodeScanner() {
        val scannerView = findViewById<CodeScannerView>(R.id.scanner_view)
        codeScanner = CodeScanner(this, scannerView)

        codeScanner.apply {
            camera = CodeScanner.CAMERA_BACK
            formats = CodeScanner.ALL_FORMATS
            autoFocusMode = AutoFocusMode.CONTINUOUS
            scanMode = ScanMode.SINGLE
            isAutoFocusEnabled = true
            isFlashEnabled = false

            decodeCallback = DecodeCallback { result ->
                runOnUiThread {
                    toneGenScan.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 300)
                    clearInfoFields()
                    findViewById<TextView>(R.id.tv_message).text = "Processing..."
                    fetchPassDetails(result.text)
                }
            }

            errorCallback = ErrorCallback { exception ->
                runOnUiThread {
                    Log.e("Main", "Camera init error:", exception)
                }
            }
        }

        scannerView.setOnClickListener {
            codeScanner.startPreview()
        }
    }

    override fun onResume() {
        super.onResume()
        codeScanner.startPreview()
    }

    override fun onPause() {
        codeScanner.releaseResources()
        super.onPause()
    }

    private fun setupPermissions() {
        val permission = ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA)
        if (permission != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.CAMERA), CAMERA_REQUEST_CODE)
        }
    }

    private fun setResultBackground(colorHex: String) {
        val container = findViewById<LinearLayout>(R.id.result_container)
        container.setBackgroundColor(android.graphics.Color.parseColor(colorHex))
    }

    private fun clearInfoFields() {
        findViewById<TextView>(R.id.tv_pass_id).text = "Pass ID: "
        findViewById<TextView>(R.id.tv_name).text = "Name: "
        findViewById<TextView>(R.id.tv_dob).text = "DOB: "
        findViewById<TextView>(R.id.tv_gender).text = "Gender: "
        findViewById<TextView>(R.id.tv_phone).text = "Phone: "
        findViewById<TextView>(R.id.tv_college).text = "College: "
        findViewById<TextView>(R.id.tv_semester).text = "Semester: "
        findViewById<ImageView>(R.id.iv_photo).setImageResource(android.R.color.darker_gray)

        setResultBackground("#FFFFFF")
    }


    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_REQUEST_CODE && (grantResults.isEmpty() || grantResults[0] != PackageManager.PERMISSION_GRANTED)) {
            Toast.makeText(this, "You need Camera Permissions to use this app!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun fetchPassDetails(passId: String) {
        val url = "https://fest-pass-585c22b308b8.herokuapp.com/verify_pass?pass_id=$passId"
        val client = OkHttpClient()
        val request = Request.Builder().url(url).build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    findViewById<TextView>(R.id.tv_message).text = "Failed to connect: ${e.localizedMessage}"
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val responseData = response.body?.string()
                runOnUiThread {
                    try {
                        val json = JSONObject(responseData!!)

                        // ❌ Case 1: Explicit error field
                        if (json.has("error")) {
                            toneGenReject.startTone(ToneGenerator.TONE_SUP_ERROR, 300)
                            setResultBackground("#F4CCCC")
                            findViewById<TextView>(R.id.tv_message).text = "Invalid Pass"
                            return@runOnUiThread
                        }

                        // ✅ Case 2: Valid response
                        if (json.has("pass_data")) {
                            val data = json.getJSONObject("pass_data")
                            toneGenAccept.startTone(ToneGenerator.TONE_PROP_ACK, 300)
                            setResultBackground("#D9EAD3")

                            findViewById<TextView>(R.id.tv_message).text = json.getString("message")
                            findViewById<TextView>(R.id.tv_pass_id).text = "Pass ID: ${data.getString("pass_id")}"
                            findViewById<TextView>(R.id.tv_name).text = "Name: ${data.getString("name")}"
                            findViewById<TextView>(R.id.tv_dob).text = "DOB: ${data.getString("dob")}"
                            findViewById<TextView>(R.id.tv_gender).text = "Gender: ${data.getString("gender")}"
                            findViewById<TextView>(R.id.tv_phone).text = "Phone: ${data.getString("phone")}"
                            findViewById<TextView>(R.id.tv_college).text = "College: ${data.getString("collegeName")}"
                            findViewById<TextView>(R.id.tv_semester).text = "Semester: ${data.getString("semester")}"

                            // Show image
                            val base64Image = data.getString("user_picture").substringAfter(",")
                            val imageBytes = Base64.decode(base64Image, Base64.DEFAULT)
                            val bmp = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                            findViewById<ImageView>(R.id.iv_photo).setImageBitmap(bmp)

                            return@runOnUiThread
                        }

                        // ⚠️ Case 3: Unknown response structure
                        setResultBackground("#FFF3CD") // optional warning-yellow
                        findViewById<TextView>(R.id.tv_message).text = "Internal Error - Contact Technical Team"
                        toneGenReject.startTone(ToneGenerator.TONE_SUP_ERROR, 300)

                    } catch (e: Exception) {
                        findViewById<TextView>(R.id.tv_message).text = "Internal Error - Contact Technical Team (Parse Error)"
                        Log.e("JSON_ERROR", "Error parsing response", e)
                    }
                }
            }

        })
    }
}
