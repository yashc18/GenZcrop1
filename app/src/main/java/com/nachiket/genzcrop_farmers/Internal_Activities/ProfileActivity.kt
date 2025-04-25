package com.nachiket.genzcrop_farmers.Internal_Activities

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.nachiket.genzcrop_farmers.LoginActivity
import com.nachiket.genzcrop_farmers.R
import com.nachiket.genzcrop_farmers.SessionManager

class ProfileActivity : AppCompatActivity() {

    private lateinit var sessionManager: SessionManager
    private lateinit var farmerName: TextView
    private lateinit var farmerCity: TextView
    private lateinit var farmerAddress: TextView
    private lateinit var farmerPhone: TextView
    private lateinit var farmSize: TextView
    private lateinit var profileImage: ImageView
    private lateinit var logoutButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        // Initialize views
        farmerName = findViewById(R.id.farmerName)
        farmerCity = findViewById(R.id.farmerCity)
        farmerAddress = findViewById(R.id.farmerAddress)
        farmerPhone = findViewById(R.id.farmerPhone)
        farmSize = findViewById(R.id.farmSize)
        profileImage = findViewById(R.id.profileImage)
        logoutButton = findViewById(R.id.logoutButton)

        // Initialize SessionManager
        sessionManager = SessionManager(this)

        // Check if the user is logged in
        if (!sessionManager.isLoggedIn()) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        // Fetch and display farmer data
        fetchFarmerData()

        // Handle Log Out button click
        logoutButton.setOnClickListener {
            sessionManager.clearSession()
            Toast.makeText(this, "Logged out successfully", Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }

    private fun fetchFarmerData() {
        val farmerId = sessionManager.getFarmerId()
        if (farmerId == null) {
            Toast.makeText(this, "Error: Farmer ID not found", Toast.LENGTH_SHORT).show()
            return
        }

        // Fetch farmer data from Realtime Database
        val database = FirebaseDatabase.getInstance()
        val farmerRef = database.getReference("Farmers").child(farmerId)

        farmerRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    // Update UI with farmer data
                    farmerName.text = snapshot.child("name").getValue(String::class.java) ?: "N/A"
                    farmerCity.text = "City: ${snapshot.child("city").getValue(String::class.java) ?: "N/A"}"
                    farmerAddress.text = "Address: ${snapshot.child("address").getValue(String::class.java) ?: "N/A"}"
                    farmerPhone.text = "Phone: +91 ${snapshot.child("number").getValue(Any::class.java) ?: "N/A"}"
                    farmSize.text = "Farm Size: ${snapshot.child("size").getValue(Double::class.java) ?: "N/A"} acres"

                    // Load profile image using Glide
                    val profileImageUrl = snapshot.child("profileImage").getValue(String::class.java)
                    if (!profileImageUrl.isNullOrEmpty()) {
                        Glide.with(this@ProfileActivity)
                            .load(profileImageUrl)
                            .placeholder(R.drawable.no_profile_pic)
                            .into(profileImage)
                    }
                } else {
                    Toast.makeText(this@ProfileActivity, "Farmer data not found", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@ProfileActivity, "Error fetching farmer data: ${error.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }
}