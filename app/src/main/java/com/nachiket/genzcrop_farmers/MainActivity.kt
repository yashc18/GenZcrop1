package com.nachiket.genzcrop_farmers

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.ValueEventListener
import com.nachiket.genzcrop_farmers.Fragments.HomeFragment
import com.nachiket.genzcrop_farmers.Fragments.HowToFragment
import com.nachiket.genzcrop_farmers.Fragments.OrderFragment
import com.nachiket.genzcrop_farmers.Fragments.AIFragment

class MainActivity : AppCompatActivity() {
    private var selectorFragment: Fragment? = null
    private val detonatorRef: DatabaseReference? = null
    private val stateListener: ValueEventListener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        initializeUI()

        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, HomeFragment())
            .commit()
    }

    override fun onResume() {
        super.onResume()
    }

    private fun initializeUI() {
        val bottomNavigationView = findViewById<BottomNavigationView>(R.id.bottom_navigation)
        bottomNavigationView.setOnNavigationItemSelectedListener(
            BottomNavigationView.OnNavigationItemSelectedListener { item ->
                val itemId = item.itemId
                if (itemId == R.id.nav_home) {
                    selectorFragment = HomeFragment()
                } else if (itemId == R.id.nav_orders) {
                    selectorFragment = OrderFragment()
                } else if (itemId == R.id.nav_how_to) {
                    selectorFragment = HowToFragment()
                } else if (itemId == R.id.nav_AI) {
                    selectorFragment = AIFragment()
                }

                if (selectorFragment != null) {
                    supportFragmentManager.beginTransaction()
                        .replace(R.id.fragment_container, selectorFragment!!)
                        .commit()
                }
                true
            })
    }

    override fun onDestroy() {
        if (stateListener != null) {
            detonatorRef!!.removeEventListener(stateListener)
        }
        super.onDestroy()
    }
}