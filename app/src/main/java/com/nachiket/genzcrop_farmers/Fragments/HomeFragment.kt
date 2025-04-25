package com.nachiket.genzcrop_farmers.Fragments

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.nachiket.genzcrop_farmers.CropsAdapter
import com.nachiket.genzcrop_farmers.Internal_Activities.AddCropActivity
import com.nachiket.genzcrop_farmers.LoginActivity
import com.nachiket.genzcrop_farmers.SessionManager
import com.nachiket.genzcrop_farmers.data_class.Crop
import com.nachiket.genzcrop_farmers.databinding.FragmentHomeBinding

class HomeFragment : Fragment() {
    private lateinit var binding: FragmentHomeBinding
    private lateinit var adapter: CropsAdapter
    private lateinit var database: DatabaseReference
    private val crops = mutableListOf<Pair<String, Crop>>()
    private lateinit var sessionManager: SessionManager

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        sessionManager = SessionManager(requireContext())

        // Check if farmer is logged in
        val farmerId = sessionManager.getFarmerId()
        if (farmerId == null) {
            // Navigate to login screen if not logged in
            startActivity(Intent(requireContext(), LoginActivity::class.java))
            requireActivity().finish()
            return
        }

        // Setup database reference with farmer ID
        database = FirebaseDatabase.getInstance().reference
            .child("Farmers")
            .child(farmerId)
            .child("crops")

        setupRecyclerView()
        setupSearch()
        setupAddButton()
        loadCrops()
    }

    private fun setupRecyclerView() {
        adapter = CropsAdapter(crops) { key ->
            deleteCrop(key)
        }
        binding.cropsRecyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = this@HomeFragment.adapter
        }
    }

    private fun setupSearch() {
        binding.searchBar.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                adapter.filter(s.toString())
            }
        })
    }

    private fun setupAddButton() {
        binding.addCropFab.setOnClickListener {
            startActivity(Intent(requireContext(), AddCropActivity::class.java))
        }
    }

    private fun loadCrops() {
        database.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val newCrops = mutableListOf<Pair<String, Crop>>()
                for (cropSnapshot in snapshot.children) {
                    val crop = cropSnapshot.getValue(Crop::class.java)?.apply {
                        name = cropSnapshot.key ?: ""
                    }
                    crop?.let {
                        newCrops.add(cropSnapshot.key!! to it)
                    }
                }
                crops.clear()
                crops.addAll(newCrops)
                adapter.updateCrops(newCrops)
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(context, "Failed to load crops: ${error.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun deleteCrop(key: String) {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete Crop")
            .setMessage("Are you sure you want to delete this crop?")
            .setPositiveButton("Yes") { _, _ ->
                database.child(key).removeValue()
                    .addOnSuccessListener {
                        Toast.makeText(context, "Crop deleted successfully", Toast.LENGTH_SHORT).show()
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(context, "Failed to delete crop: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
            }
            .setNegativeButton("No", null)
            .show()
    }
}