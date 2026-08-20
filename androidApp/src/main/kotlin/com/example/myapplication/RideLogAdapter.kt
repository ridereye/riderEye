package com.example.myapplication

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RideLogAdapter(private val logList: List<RideLog>) :
    RecyclerView.Adapter<RideLogAdapter.LogViewHolder>() {

    class LogViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvStatus: TextView = itemView.findViewById(R.id.tv_log_status)
        val tvStart: TextView = itemView.findViewById(R.id.tv_log_start)
        val tvEnd: TextView = itemView.findViewById(R.id.tv_log_end)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LogViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_ride_log, parent, false)
        return LogViewHolder(view)
    }

    override fun onBindViewHolder(holder: LogViewHolder, position: Int) {
        val log = logList[position]
        val sdf = SimpleDateFormat("MMM dd, yyyy - hh:mm a", Locale.getDefault())

        // Ipinapasa natin ang mga bagong fields sa iyong existing TextViews
        holder.tvStatus.text = "Speed: ${log.speed} km/h"
        holder.tvStart.text = "Timestamp: ${sdf.format(Date(log.timestamp))}"
        holder.tvEnd.text = "Stop duration: ${log.stop_duration}"
    }

    override fun getItemCount(): Int = logList.size
}