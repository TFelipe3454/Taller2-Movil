package com.example.taller2

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView

import androidx.recyclerview.widget.RecyclerView

class ContactAdapter(private val items: List<Contact>, private val context: Context) :
    RecyclerView.Adapter<ContactAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.item_contact, parent, false)
        return ViewHolder(view)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val contact = items[position]
        holder.name.text = contact.name
        holder.number.text = contact.number
        if (contact.image != null) {
            holder.profile.setImageBitmap(contact.image)
        } else {
            holder.profile.setImageResource(R.drawable.img_user)
        }
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.name)
        val number: TextView = view.findViewById(R.id.Id)
        val profile: ImageView = view.findViewById(R.id.photo)
    }
}
