package com.example.myapplication

import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import java.util.Locale

/**
 * Reusable adapter para sa "Nearest Emergency" type ng listahan.
 * Ginagamit ito ng MapFragment (may Go-to-map behavior) at SafetyFragment
 * (may open-external-navigation behavior) — kaya ang "Go" click ay callback na lang,
 * pero ang "Call" ay pareho lagi kaya nasa loob mismo ng adapter.
 *
 * @param onGoClick tatawagin kapag pinindot ang "Go" button ng isang item.
 *                   Ang bawat fragment ang magdedesisyon kung ano ang gagawin
 *                   (mag-draw ng route sa sariling MapView, o mag-launch ng
 *                   external Maps app).
 */
class NearbyPlaceAdapter(
    private var places: List<NearbyPlace>,
    private val onGoClick: (NearbyPlace) -> Unit
) : RecyclerView.Adapter<NearbyPlaceAdapter.PlaceViewHolder>() {

    inner class PlaceViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvIcon: TextView = view.findViewById(R.id.tv_item_icon)
        val tvName: TextView = view.findViewById(R.id.tv_item_hospital_name)
        val tvDistance: TextView = view.findViewById(R.id.tv_item_hospital_distance)
        val btnCall: ImageButton = view.findViewById(R.id.btn_item_call)
        val btnGo: ImageButton = view.findViewById(R.id.btn_item_go)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PlaceViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_hospital, parent, false)
        return PlaceViewHolder(view)
    }

    override fun onBindViewHolder(holder: PlaceViewHolder, position: Int) {
        val place = places[position]
        holder.tvName.text = place.name

        when (place.type) {
            "hospital" -> {
                holder.tvIcon.text = "🏥"
                holder.tvDistance.text = String.format(Locale.getDefault(), "Hospital • %.1fkm", place.distanceKm)
            }
            "police" -> {
                holder.tvIcon.text = "🛡️"
                holder.tvDistance.text = String.format(Locale.getDefault(), "Police Station • %.1fkm", place.distanceKm)
            }
            "fuel" -> {
                holder.tvIcon.text = "⛽"
                holder.tvDistance.text = String.format(Locale.getDefault(), "Gas Station • %.1fkm", place.distanceKm)
            }
            else -> {
                holder.tvIcon.text = "📍"
                holder.tvDistance.text = String.format(Locale.getDefault(), "Lugar • %.1fkm", place.distanceKm)
            }
        }

        holder.btnCall.setOnClickListener {
            if (!place.hasRealPhone) {
                Toast.makeText(
                    holder.itemView.context,
                    "Walang nakarehistrong numero ang ${place.name} sa mapa. Tumatawag sa 911.",
                    Toast.LENGTH_LONG
                ).show()
            }
            val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse(place.phone))
            holder.itemView.context.startActivity(dialIntent)
        }

        holder.btnGo.setOnClickListener {
            onGoClick(place)
        }
    }

    override fun getItemCount() = places.size

    fun updateData(newPlaces: List<NearbyPlace>) {
        places = newPlaces
        notifyDataSetChanged()
    }
}