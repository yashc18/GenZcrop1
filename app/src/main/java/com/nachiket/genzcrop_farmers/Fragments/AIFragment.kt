// AIFragment.java
package com.nachiket.genzcrop_farmers.Fragments

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.fragment.app.Fragment
import com.nachiket.genzcrop_farmers.R
import com.nachiket.genzcrop_farmers.Internal_Activities.LocationAnalysisActivity
import com.nachiket.genzcrop_farmers.Internal_Activities.PlantHealthActivity
import com.nachiket.genzcrop_farmers.Internal_Activities.SoilTestActivity

class AIFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        val root: View = inflater.inflate(R.layout.fragment_a_i, container, false)

        val btnPlantHealth = root.findViewById<Button>(R.id.btnPlantHealth)
        val btnSoilTest = root.findViewById<Button>(R.id.btnSoilTestReport)
        val btnLocationAnalysis = root.findViewById<Button>(R.id.btnSoilLocation)

        btnPlantHealth.setOnClickListener(View.OnClickListener { v: View? ->
            val intent = Intent(
                activity,
                PlantHealthActivity::class.java
            )
            startActivity(intent)
        })

        btnSoilTest.setOnClickListener(View.OnClickListener { v: View? ->
            val intent = Intent(
                activity,
                SoilTestActivity::class.java
            )
            startActivity(intent)
        })

        btnLocationAnalysis.setOnClickListener(View.OnClickListener { v: View? ->
            val intent = Intent(
                activity,
                LocationAnalysisActivity::class.java
            )
            startActivity(intent)
        })

        return root
    }
}