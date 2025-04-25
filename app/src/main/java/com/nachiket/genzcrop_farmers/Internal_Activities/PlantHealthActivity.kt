package com.nachiket.genzcrop_farmers.Internal_Activities

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Typeface
import android.os.Bundle
import android.provider.MediaStore
import android.text.Spannable
import android.text.SpannableString
import android.text.style.StyleSpan
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.nachiket.genzcrop_farmers.R
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.regex.Pattern

class PlantHealthActivity : AppCompatActivity() {

    private lateinit var imageView: ImageView
    private lateinit var resultText: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var model: GenerativeModel

    companion object {
        private const val CAMERA_PERMISSION_CODE = 100
        private const val CAMERA_REQUEST_CODE = 101
        private const val GALLERY_REQUEST_CODE = 102
        private const val API_KEY = "AIzaSyCUTqJVg1f9MxrfY5x46jUn36eKst_sBCQ"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_plant_health)

        imageView = findViewById(R.id.imageView)
        resultText = findViewById(R.id.resultText)
        progressBar = findViewById(R.id.progressBar)

        // Initialize Gemini model
        model = GenerativeModel("gemini-2.0-flash", API_KEY)

        showChoiceDialog()
    }

    private fun showChoiceDialog() {
        AlertDialog.Builder(this).apply {
            setTitle("Choose Image Source")
            setMessage("How would you like to provide the plant image?")
            setCancelable(false)
            setPositiveButton("Camera") { _, _ -> checkCameraPermission() }
            setNegativeButton("Gallery") { _, _ -> openGallery() }
            show()
        }
    }

    private fun checkCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED) {

            if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.CAMERA)) {
                // Show explanation dialog
                AlertDialog.Builder(this)
                    .setTitle("Camera Permission Needed")
                    .setMessage("This app needs camera permission to take pictures. Please allow it in settings.")
                    .setPositiveButton("Open Settings") { _, _ ->
                        val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                        intent.data = android.net.Uri.parse("package:$packageName")
                        startActivity(intent)
                    }
                    .setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }
                    .show()
            } else {
                // Request permission
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.CAMERA),
                    CAMERA_PERMISSION_CODE
                )
            }
        } else {
            openCamera()
        }
    }



    private fun openCamera() {
        val cameraIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        if (cameraIntent.resolveActivity(packageManager) != null) {
            startActivityForResult(cameraIntent, CAMERA_REQUEST_CODE)
        } else {
            Toast.makeText(this, "No camera app available", Toast.LENGTH_SHORT).show()
        }
    }


    private fun openGallery() {
        Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI).also { intent ->
            startActivityForResult(intent, GALLERY_REQUEST_CODE)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == CAMERA_PERMISSION_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Open camera if permission is granted
                openCamera()
            } else {
                // Show a dialog explaining why the permission is needed
                AlertDialog.Builder(this)
                    .setTitle("Camera Permission Needed")
                    .setMessage("This app requires camera permission to capture images.")
                    .setPositiveButton("Grant") { _, _ ->
                        ActivityCompat.requestPermissions(
                            this,
                            arrayOf(Manifest.permission.CAMERA),
                            CAMERA_PERMISSION_CODE
                        )
                    }
                    .setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }
                    .show()
            }
        }
    }


    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (resultCode == RESULT_OK) {
            try {
                val imageBitmap: Bitmap? = when (requestCode) {
                    CAMERA_REQUEST_CODE -> data?.extras?.get("data") as Bitmap
                    GALLERY_REQUEST_CODE -> {
                        val uri = data?.data
                        MediaStore.Images.Media.getBitmap(contentResolver, uri)
                    }
                    else -> null
                }

                imageBitmap?.let {
                    imageView.setImageBitmap(it)
                    analyzePlantHealth(it)
                }
            } catch (e: Exception) {
                Toast.makeText(this, "Error processing image", Toast.LENGTH_SHORT).show()
            }
        } else {
            showChoiceDialog()
        }
    }

    private fun formatBoldText(text: String): CharSequence {
        val spannableString = SpannableString(text)

        // Pattern for double asterisks (bold text)
        val boldPattern = Pattern.compile("\\*\\*(.*?)\\*\\*")
        val boldMatcher = boldPattern.matcher(text)

        // Apply bold style to text between double asterisks
        while (boldMatcher.find()) {
            val start = boldMatcher.start()
            val end = boldMatcher.end()
            spannableString.setSpan(
                StyleSpan(Typeface.BOLD),
                start,
                end,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }

        // Create final text by removing double asterisks
        val finalText = spannableString.toString().replace("**", "")
        return SpannableString(finalText).apply {
            // Transfer spans from original to final text
            spannableString.getSpans(0, spannableString.length, StyleSpan::class.java).forEach { span ->
                val spanStart = spannableString.getSpanStart(span)
                val spanEnd = spannableString.getSpanEnd(span)
                val spanFlags = spannableString.getSpanFlags(span)

                // Calculate new positions after removing asterisks
                val newStart = calculateNewPosition(spanStart, text)
                val newEnd = calculateNewPosition(spanEnd, text)

                if (newStart < newEnd && newEnd <= finalText.length) {
                    setSpan(StyleSpan(Typeface.BOLD), newStart, newEnd, spanFlags)
                }
            }
        }
    }

    private fun calculateNewPosition(position: Int, originalText: String): Int {
        // Count double asterisks before the position
        val asteriskPairs = originalText.substring(0, position).split("**").size - 1
        // Each pair of asterisks reduces position by 2 characters
        return position - (asteriskPairs * 2)
    }

    private fun analyzePlantHealth(photo: Bitmap) {
        progressBar.visibility = View.VISIBLE
        resultText.text = "Analyzing image..."

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val prompt = """
                   Analyze the provided plant leaf image and determine its health condition. Start your response with a one-word summary ('Healthy' or 'Unhealthy') wrapped in double asterisks (**). Then, provide a structured analysis covering:

                    Plant Health Status – A brief overall assessment.
                    Detected Diseases or Deficiencies – Any visible symptoms with possible causes.
                    Treatment Recommendations – Specific actionable solutions.
                    Prevention Measures – Steps to prevent recurrence of identified issues.
                    Best Practices for Plant Care – General guidelines for maintaining optimal plant health.
                    If the image does not appear to be a plant or leaf, respond with: 'Please provide a valid plant or leaf image.' Avoid using single asterisks (*) for spacing.
                    keep the overall response short and brief.
                """.trimIndent()

                val content = content {
                    image(photo)
                    text(prompt)
                }


                val response = model.generateContent(content)
                val analysis = response.text ?: "Unable to generate analysis"

                withContext(Dispatchers.Main) {
                    Log.i("uttar", analysis)
                    val formattedText = formatBoldText(analysis)
                    resultText.text = formattedText
                    resultText.setText(formattedText, TextView.BufferType.SPANNABLE)
                    progressBar.visibility = View.GONE
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    resultText.text = "Error: ${e.message}"
                    progressBar.visibility = View.GONE
                }
            }
        }
    }
}