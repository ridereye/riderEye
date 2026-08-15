package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class AdminHomeActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin_home)

        val btnManage = findViewById<Button>(R.id.btn_manage_users)
        val btnLogout = findViewById<Button>(R.id.btn_admin_logout)

        btnManage.setOnClickListener {
            Toast.makeText(this, "Opening User Management Panel...", Toast.LENGTH_SHORT).show()
        }

        btnLogout.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }
}