package com.example.taller2

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.ContactsContract
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.taller2.databinding.ActivityContactsBinding

class Contacts : AppCompatActivity() {

    private lateinit var binding: ActivityContactsBinding
    private var contactList: MutableList<Contact> = ArrayList()

    companion object {
        private const val PERMISSION_REQUEST_READ_CONTACTS = 100
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityContactsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.contactList.layoutManager = LinearLayoutManager(this)

        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_CONTACTS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.READ_CONTACTS),
                PERMISSION_REQUEST_READ_CONTACTS
            )
        } else {
            loadContacts()
        }
    }

    @SuppressLint("Range")
    private fun loadContacts() {
        val contacts = contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            null, null, null, null
        )

        if (contacts != null) {
            while (contacts.moveToNext()) {
                val name =
                    contacts.getString(contacts.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME))
                val number =
                    contacts.getString(contacts.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER))
                val contact = Contact(name, number)
                contactList.add(contact)
            }
            contacts.close()
        }

        binding.contactList.adapter = ContactAdapter(contactList, this)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == PERMISSION_REQUEST_READ_CONTACTS) {
            if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                loadContacts()
            } else {
                Toast.makeText(this, "Permiso para leer contactos denegado", Toast.LENGTH_SHORT).show()
            }
        }
    }

}



