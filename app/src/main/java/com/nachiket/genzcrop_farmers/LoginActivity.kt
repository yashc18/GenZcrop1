package com.nachiket.genzcrop_farmers

import android.content.Intent
import android.os.Bundle
import android.text.TextUtils
import android.view.View
import android.widget.*
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.firebase.database.*
import com.nachiket.genzcrop_farmers.databinding.ActivityLoginBinding

class LoginActivity : AppCompatActivity() {
    private lateinit var binding: ActivityLoginBinding
    private lateinit var database: DatabaseReference
    private lateinit var sessionManager: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sessionManager = SessionManager(this)

        // If already logged in, go to home screen
        if (sessionManager.isLoggedIn()) {
            startHomeActivity()
            finish()
            return
        }

        database = FirebaseDatabase.getInstance().reference.child("Farmers")

        binding.btnLoginNow.setOnClickListener {
            val farmerId = binding.txtid.text.toString()
            val password = binding.txtPassword.text.toString()

            if (farmerId.isNotEmpty() && password.isNotEmpty()) {
                loginFarmer(farmerId, password)
            }
        }
    }

    private fun loginFarmer(farmerId: String, password: String) {
        database.child(farmerId).get().addOnSuccessListener { snapshot ->
            if (snapshot.exists()) {
                val storedPassword = snapshot.child("password").getValue(String::class.java)
                if (storedPassword == password) {
                    // Save session
                    sessionManager.saveFarmerSession(farmerId)
                    startHomeActivity()
                    finish()
                } else {
                    Toast.makeText(this, "Invalid password", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "Farmer ID not found", Toast.LENGTH_SHORT).show()
            }
        }.addOnFailureListener {
            Toast.makeText(this, "Login failed: ${it.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startHomeActivity() {
        startActivity(Intent(this, MainActivity::class.java))
    }
}
