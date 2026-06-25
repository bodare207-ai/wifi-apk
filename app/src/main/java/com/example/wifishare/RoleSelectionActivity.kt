package com.example.wifishare

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

class RoleSelectionActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_role_selection)

        findViewById<Button>(R.id.senderButton).setOnClickListener {
            startActivity(Intent(this, SenderActivity::class.java))
        }

        findViewById<Button>(R.id.receiverButton).setOnClickListener {
            startActivity(Intent(this, ReceiverActivity::class.java))
        }
    }
}