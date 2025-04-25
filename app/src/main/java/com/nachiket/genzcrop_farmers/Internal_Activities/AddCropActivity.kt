package com.nachiket.genzcrop_farmers.Internal_Activities

import android.app.ProgressDialog
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.storageMetadata
import com.nachiket.genzcrop_farmers.R
import com.nachiket.genzcrop_farmers.SessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.io.IOException
import java.net.URL
import java.util.regex.Pattern

class AddCropActivity : AppCompatActivity() {
    private val selectedImages = mutableListOf<Uri>()
    private val storage = FirebaseStorage.getInstance()
    private val database = FirebaseDatabase.getInstance()
    private val sessionManager by lazy { SessionManager(this) }
    private val client = OkHttpClient()
    private var progressDialog: ProgressDialog? = null

    private val generativeModel by lazy {
        GenerativeModel(
            modelName = "gemini-2.0-flash",
            apiKey = "//API KEY"
        )
    }


    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.size == 3) {
            selectedImages.clear()
            selectedImages.addAll(uris)
            updateImagePreview()
        } else {
            Toast.makeText(this, "Please select exactly 3 images", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_crop)

        // Initialize Firebase Storage with settings
        val storageRef = storage.reference
        storageRef.storage.maxUploadRetryTimeMillis = 50000
        storageRef.storage.maxOperationRetryTimeMillis = 50000

        findViewById<Button>(R.id.cropBtn).setOnClickListener {
            imagePickerLauncher.launch("image/*")
        }

        findViewById<Button>(R.id.saveCropBtn).setOnClickListener {
            if (validateInputs()) {
                lifecycleScope.launch {
                    saveCropData()
                }
            }
        }
    }

    private fun updateImagePreview() {
        // Update the ImageView to show the first selected image
        val imageView = findViewById<ImageView>(R.id.crop)
        imageView.setImageURI(selectedImages.firstOrNull())
    }

    private suspend fun uploadImagesToFirebase(farmerId: String, cropName: String): List<String> {
        return withContext(Dispatchers.IO) {
            selectedImages.mapIndexed { index, uri ->
                try {
                    val imageRef = storage.reference
                        .child("crops")
                        .child(farmerId)
                        .child(cropName)
                        .child("image${index + 1}.jpg")

                    // Convert Uri to byte array
                    val inputStream = contentResolver.openInputStream(uri)
                    val bytes = inputStream?.readBytes()
                    inputStream?.close()

                    if (bytes == null) {
                        throw IOException("Could not read image file")
                    }

                    // Upload bytes with metadata
                    val metadata = storageMetadata {
                        contentType = "image/jpeg"
                    }

                    val uploadTask = imageRef.putBytes(bytes, metadata).await()
                    imageRef.downloadUrl.await().toString()
                } catch (e: Exception) {
                    Log.e("UploadError", "Error uploading image ${index + 1}: ${e.message}")
                    throw e
                }
            }
        }
    }

    private suspend fun analyzeImagesWithGemini(imageUrls: List<String>): Pair<String, String> {
        return try {
            // Load images as bitmaps
            val bitmaps = imageUrls.map { url ->
                withContext(Dispatchers.IO) {
                    val connection = URL(url).openConnection()
                    connection.doInput = true
                    connection.connect()
                    val input = connection.getInputStream()
                    BitmapFactory.decodeStream(input)
                }
            }

            // Create analysis prompt
            val prompt = """
            *Crop Health Assessment Request*
            
            As an agricultural expert, analyze these 3 crop images and provide:
            
            1. *Grade*: Single letter grade (A+, A, B+, B, C) based on:
               - Visual health indicators
               - Growth pattern consistency
               - Pest/disease signs
               
            2. *Analysis*: Brief 15-20 word summary of:
               - Overall crop condition
               - Notable health aspects
               - Key observations
            
            *Response Format:*
            GRADE: [VALID_GRADE]
            ANALYSIS: [CONCISE_SUMMARY]
            
            Invalid formats will be rejected.
        """.trimIndent()

            // Generate content
            val content = content {
                for (bitmap in bitmaps) {
                    image(bitmap)
                }
                text(prompt)
            }

            val response = generativeModel.generateContent(content)
            Log.i("response",response.toString())
            val rawText = response.text ?: throw Exception("Empty response from AI")

            // Parse response with enhanced pattern matching
            val (grade, analysis) = parseGeminiResponse(rawText)

            Pair(grade, analysis)
        } catch (e: Exception) {
            Log.e("GeminiAnalysis", "Error: ${e.message}", e)
            Pair("B", "Crop shows typical growth patterns with moderate health indicators.")
        }
    }

    // New helper function for response parsing
    private fun parseGeminiResponse(rawText: String): Pair<String, String> {
        val gradePattern = Pattern.compile("GRADE:\\s*([A+ABC])", Pattern.CASE_INSENSITIVE)
        val analysisPattern = Pattern.compile("ANALYSIS:\\s*(.+?)(?=\\n|$)", Pattern.DOTALL)

        var grade = "B"
        var analysis = ""

        // Grade extraction
        val gradeMatcher = gradePattern.matcher(rawText)
        if (gradeMatcher.find()) {
            grade = gradeMatcher.group(1)?.uppercase() ?: "B"
        }

        // Analysis extraction
        val analysisMatcher = analysisPattern.matcher(rawText)
        if (analysisMatcher.find()) {
            analysis = analysisMatcher.group(1)?.trim() ?: ""
        }

        // Validation and fallbacks
        grade = when (grade.uppercase()) {
            "A+", "A" -> "A"
            "B+" -> "B+"
            "B" -> "B"
            "C" -> "C"
            else -> "B"
        }

        if (analysis.isBlank()) {
            analysis = "Crop appears to be in average condition with normal growth patterns."
        }

        return Pair(grade, analysis)
    }

    private suspend fun saveCropData() {
        try {
            showLoadingDialog()

            val farmerId = sessionManager.getFarmerId() ?: throw IllegalStateException("Farmer ID is missing")
            val cropName = findViewById<TextInputEditText>(R.id.cropNameEt).text.toString()
            val variety = findViewById<TextInputEditText>(R.id.varietyEt).text.toString()
            val quantity = findViewById<TextInputEditText>(R.id.quantityEt).text.toString().toInt()
            val price = findViewById<TextInputEditText>(R.id.priceEt).text.toString().toDouble()
            val readyDate = findViewById<TextInputEditText>(R.id.readyDateEt).text.toString()

            // Upload images and get URLs
            val imageUrls = uploadImagesToFirebase(farmerId, cropName)

            // Analyze images with Gemini
            val (grade, analysis) = analyzeImagesWithGemini(imageUrls)

            // Create crop data
            val cropData = hashMapOf(
                "variety" to variety,
                "quantity" to quantity,
                "price" to price,
                "ready_date" to readyDate,
                "image1" to imageUrls[0],
                "image2" to imageUrls[1],
                "image3" to imageUrls[2],
                "grade" to grade,
                "analysis" to analysis,
                "stage" to 1
            )

            // Save to Firebase Database
            database.reference
                .child("Farmers")
                .child(farmerId)
                .child("crops")
                .child(cropName)
                .setValue(cropData)
                .await()

            hideLoadingDialog()
            Toast.makeText(this, "Crop added successfully", Toast.LENGTH_SHORT).show()
            finish()

        } catch (e: Exception) {
            hideLoadingDialog()
            Log.e("SaveCropError", "Error saving crop data: ${e.message}")
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun validateInputs(): Boolean {
        if (selectedImages.size != 3) {
            Toast.makeText(this, "Please select exactly 3 images", Toast.LENGTH_SHORT).show()
            return false
        }

        // Validate other fields
        val cropName = findViewById<TextInputEditText>(R.id.cropNameEt).text.toString()
        val variety = findViewById<TextInputEditText>(R.id.varietyEt).text.toString()
        val quantity = findViewById<TextInputEditText>(R.id.quantityEt).text.toString()
        val price = findViewById<TextInputEditText>(R.id.priceEt).text.toString()
        val readyDate = findViewById<TextInputEditText>(R.id.readyDateEt).text.toString()

        if (cropName.isEmpty() || variety.isEmpty() || quantity.isEmpty() ||
            price.isEmpty() || readyDate.isEmpty()) {
            Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
            return false
        }

        return true
    }

    private fun showLoadingDialog() {
        progressDialog = ProgressDialog(this)
        progressDialog?.setMessage("Uploading...")
        progressDialog?.setCancelable(false)
        progressDialog?.show()
    }

    private fun hideLoadingDialog() {
        progressDialog?.dismiss()
    }
}
