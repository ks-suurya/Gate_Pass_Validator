package com.example.invitevalidator

import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.budiyev.android.codescanner.AutoFocusMode
import com.budiyev.android.codescanner.CodeScanner
import com.budiyev.android.codescanner.CodeScannerView
import com.budiyev.android.codescanner.DecodeCallback
import com.budiyev.android.codescanner.ErrorCallback
import com.budiyev.android.codescanner.ScanMode
import android.graphics.BitmapFactory
import android.util.Base64
import android.widget.ImageView
import okhttp3.*
import java.io.IOException
import org.json.JSONObject


private const val CAMERA_REQUEST_CODE = 101

class MainActivity : AppCompatActivity() {
    private lateinit var codeScanner: CodeScanner

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        setupPermissions()
        codeScanner()
    }

    private fun codeScanner(){
        val scannerView = findViewById<CodeScannerView>(R.id.scanner_view)

        codeScanner = CodeScanner(this, scannerView)

        codeScanner.apply {
            camera = CodeScanner.CAMERA_BACK
            formats = CodeScanner.ALL_FORMATS

            autoFocusMode = AutoFocusMode.SAFE
            scanMode = ScanMode.SINGLE
            isAutoFocusEnabled = true
            isFlashEnabled = false

            codeScanner.decodeCallback = DecodeCallback {result ->
                runOnUiThread {
                    val scannedId = result.text
                    fetchPassDetails(scannedId)
                }
            }

            errorCallback = ErrorCallback { exception ->
                runOnUiThread{
                    Log.e("Main", "Camera init error:", exception)
                }
            }
        }

        scannerView.setOnClickListener{
            codeScanner.startPreview()
        }
    }

    override fun onResume(){
        super.onResume()
        codeScanner.startPreview()
    }

    override fun onPause() {
        codeScanner.releaseResources()
        super.onPause()
    }

    private fun setupPermissions() {
        val permission = ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA)

        if (permission != PackageManager.PERMISSION_GRANTED){
            makeRequest()
        }
    }

    private fun makeRequest(){
        ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.CAMERA), CAMERA_REQUEST_CODE)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray){
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            CAMERA_REQUEST_CODE -> {
                if (grantResults.isEmpty() || grantResults[0] != PackageManager.PERMISSION_GRANTED){
                    Toast.makeText(this, "You need Camera Permissions to use this app!", Toast.LENGTH_SHORT).show()
                }
                else {
                    // successful
                }
            }
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
                        if (json.has("error")) {
                            findViewById<TextView>(R.id.tv_message).text = "Invalid Pass"
                            return@runOnUiThread
                        }

                        val data = json.getJSONObject("pass_data")
                        findViewById<TextView>(R.id.tv_message).text = json.getString("message")
                        findViewById<TextView>(R.id.tv_pass_id).text = "Pass ID: ${data.getString("pass_id")}"
                        findViewById<TextView>(R.id.tv_name).text = "Name: ${data.getString("name")}"
                        findViewById<TextView>(R.id.tv_dob).text = "DOB: ${data.getString("dob")}"
                        findViewById<TextView>(R.id.tv_gender).text = "Gender: ${data.getString("gender")}"
                        findViewById<TextView>(R.id.tv_phone).text = "Phone: ${data.getString("phone")}"
                        findViewById<TextView>(R.id.tv_college).text = "College: ${data.getString("collegeName")}"
                        findViewById<TextView>(R.id.tv_semester).text = "Semester: ${data.getString("semester")}"

                        // Decode and show base64 image
                        val base64Image = data.getString("user_picture").substringAfter(",")
                        val imageBytes = Base64.decode(base64Image, Base64.DEFAULT)
                        val bmp = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                        findViewById<ImageView>(R.id.iv_photo).setImageBitmap(bmp)

                    } catch (e: Exception) {
                        findViewById<TextView>(R.id.tv_message).text = "Error parsing data"
                    }
                }
            }
        })
    }

}
