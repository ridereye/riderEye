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

        holder.tvStatus.text = "Status: ${log.status}"
        holder.tvStart.text = "Start: ${log.start_time?.let { sdf.format(Date(it)) } ?: "N/A"}"
        holder.tvEnd.text = "End: ${log.end_time?.let { sdf.format(Date(it)) } ?: "In Progress..."}"
    }

    override fun getItemCount(): Int = logList.size
}