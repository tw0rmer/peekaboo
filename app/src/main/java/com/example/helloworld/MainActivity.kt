package com.example.helloworld

import android.Manifest
import android.content.pm.PackageManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.util.Log
import android.provider.Telephony
import android.database.Cursor
import android.net.Uri

class MainActivity : AppCompatActivity() {

    private val READ_SMS_PERMISSION_CODE = 101

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

    private fun readSmsMessages() {
        Log.d("MainActivity", "Read SMS permission granted. Reading messages...")
        Toast.makeText(this, "Reading SMS...", Toast.LENGTH_SHORT).show()

        val messages = mutableListOf<String>()
        val cursor: Cursor? = contentResolver.query(
            Telephony.Sms.Inbox.CONTENT_URI,
            null, // Projection: null returns all columns
            null, // Selection: null returns all rows
            null, // Selection args
            Telephony.Sms.Inbox.DEFAULT_SORT_ORDER // Sort order
        )

        cursor?.use { // Use ensures the cursor is closed automatically
            if (it.moveToFirst()) {
                val addressIndex = it.getColumnIndex(Telephony.Sms.Inbox.ADDRESS)
                val bodyIndex = it.getColumnIndex(Telephony.Sms.Inbox.BODY)
                // Check if columns exist before accessing them
                if (addressIndex != -1 && bodyIndex != -1) {
                    do {
                        val address = it.getString(addressIndex) ?: "Unknown Address"
                        val body = it.getString(bodyIndex) ?: "Empty Body"
                        val message = "From: $address\nBody: $body\n---"
                        messages.add(message)
                        Log.d("MainActivity", message) // Log each message
                    } while (it.moveToNext())
                } else {
                     Log.e("MainActivity", "Required SMS columns (Address or Body) not found.")
                     Toast.makeText(this, "Error reading SMS columns", Toast.LENGTH_SHORT).show()
                }
            } else {
                Log.d("MainActivity", "No SMS messages found in Inbox.")
                Toast.makeText(this, "No SMS messages found", Toast.LENGTH_SHORT).show()
            }
        } ?: run {
            Log.e("MainActivity", "Could not query SMS inbox.")
            Toast.makeText(this, "Error reading SMS", Toast.LENGTH_SHORT).show()
        }

        // Optional: Display messages in UI or process them further
        // For now, we are just logging them.
        Log.d("MainActivity", "Finished reading ${messages.size} messages.")
        if (messages.isNotEmpty()) {
             Toast.makeText(this, "Read ${messages.size} SMS messages (check logs)", Toast.LENGTH_LONG).show()
        }
    }
}