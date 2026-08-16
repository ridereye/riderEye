package com.example.myapplication

import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

class HistoryActivity : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()
    private val logList = mutableListOf<RideLog>()
    private lateinit var adapter: RideLogAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)

        val riderUsername = intent.getStringExtra("RIDER_USERNAME") ?: ""

        val rvLogs = findViewById<RecyclerView>(R.id.rv_ride_logs)
        val btnBack = findViewById<Button>(R.id.btn_back_history)

        adapter = RideLogAdapter(logList)
        rvLogs.layoutManager = LinearLayoutManager(this)
        rvLogs.adapter = adapter

        btnBack.setOnClickListener { finish() }

        if (riderUsername.isNotEmpty()) {
            loadRideHistory(riderUsername)
        } else {
            Toast.makeText(this, "No linked rider specified!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadRideHistory(username: String) {
        db.collection("ride_logs")
            .whereEqualTo("rider_username", username)
            .orderBy("start_time", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, e ->
                if (e != null || snapshot == null) return@addSnapshotListener

                logList.clear()
                for (doc in snapshot.documents) {
                    val log = doc.toObject(RideLog::class.java)
                    if (log != null) {
                        logList.add(log)
                    }
                }
                adapter.notifyDataSetChanged()
            }
    }
}