package com.example.taller2

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.taller2.databinding.ActivityMainBinding
import com.example.taller2.databinding.ItemContactBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.ButtonContacts.setOnClickListener{
            val i = Intent(baseContext, Contacts::class.java)
            startActivity(i)
        }

        binding.buttonGallery.setOnClickListener{
            val i = Intent(baseContext, Image::class.java)
            startActivity(i)
        }

        binding.ButtonMap.setOnClickListener{
            val i = Intent(baseContext, Mapas::class.java)
            startActivity(i)
        }
    }
}