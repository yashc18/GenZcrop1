package com.nachiket.genzcrop_farmers.Internal_Activities

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.text.Spannable
import android.text.SpannableString
import android.text.style.StyleSpan
import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.nachiket.genzcrop_farmers.R
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.itextpdf.text.pdf.PdfReader
import com.itextpdf.text.pdf.parser.PdfTextExtractor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.regex.Pattern

class SoilTestActivity : AppCompatActivity() {

    private lateinit var resultText: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var reportPreview: ImageView
    private lateinit var model: GenerativeModel

    companion object {
        private const val PDF_REQUEST_CODE = 103
        private const val IMAGE_REQUEST_CODE = 104
        private const val API_KEY = "//API KEY"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_soil_test)

        resultText = findViewById(R.id.resultText)
        progressBar = findViewById(R.id.progressBar)
        reportPreview = findViewById(R.id.reportPreview)

        // Initialize Gemini model
        model = GenerativeModel("gemini-2.0-flash", API_KEY)

        showChoiceDialog()
    }

    private fun showChoiceDialog() {
        AlertDialog.Builder(this).apply {
            setTitle("Choose Report Format")
            setMessage("How would you like to provide the soil test report?")
            setCancelable(false)
            setPositiveButton("PDF Report") { _, _ -> openPdfPicker() }
            setNegativeButton("Image of Report") { _, _ -> openImagePicker() }
            show()
        }
    }

    private fun openPdfPicker() {
        reportPreview.visibility = View.GONE
        Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "application/pdf"
            startActivityForResult(this, PDF_REQUEST_CODE)
        }
    }

    private fun openImagePicker() {
        Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI).also { intent ->
            startActivityForResult(intent, IMAGE_REQUEST_CODE)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (resultCode == RESULT_OK && data != null) {
            val fileUri = data.data

            when (requestCode) {
                PDF_REQUEST_CODE -> fileUri?.let { analyzePdfReport(it) }
                IMAGE_REQUEST_CODE -> try {
                    val bitmap = MediaStore.Images.Media.getBitmap(contentResolver, fileUri)
                    reportPreview.setImageBitmap(bitmap)
                    analyzeImageReport(bitmap)
                } catch (e: Exception) {
                    Toast.makeText(this, "Error loading image", Toast.LENGTH_SHORT).show()
                    showChoiceDialog()
                }
            }
        } else {
            showChoiceDialog()
        }
    }

    private fun analyzePdfReport(pdfUri: Uri) {
        progressBar.visibility = View.VISIBLE
        resultText.text = "Analyzing PDF report..."

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val pdfText = extractTextFromPdf(pdfUri)

                val prompt = """
                    Analyze this soil test report and provide:
                    1. Current soil health status
                    2. Key nutrient levels and their implications
                    3. Specific recommendations for soil improvement
                    4. Suitable crops for this soil type
                    5. Required amendments and their application rates
                    
                    Report content:
                    $pdfText
                """.trimIndent()

                val content = content {
                    text(prompt)
                }

                val response = model.generateContent(content)
                val analysis = response.text ?: "Unable to generate analysis"

                withContext(Dispatchers.Main) {
                    resultText.text = analysis
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

    private fun formatBoldText(text: String): CharSequence {
        val pattern = Pattern.compile("\\*(.*?)\\*")
        val matcher = pattern.matcher(text)
        val spannableString = SpannableString(text)

        while (matcher.find()) {
            val start = matcher.start()
            val end = matcher.end()
            val content = matcher.group(1) // Text between asterisks

            if (content != null) {
                // Remove asterisks and apply bold style
                spannableString.setSpan(
                    StyleSpan(Typeface.BOLD),
                    start,
                    end,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        }

        // Remove the asterisk characters while keeping the bold formatting
        return spannableString.toString().replace("*", "")
    }

    private fun analyzeImageReport(reportImage: Bitmap) {
        progressBar.visibility = View.VISIBLE
        resultText.text = "Analyzing report image..."

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val prompt = """
                    This is an image of a soil test report. Analyze it if the harvest will be more or less or whatever in the start and then provide:
                    1. Current soil health status
                    2. Key nutrient levels and their implications
                    3. Specific recommendations for soil improvement
                    4. Suitable crops for this soil type
                    5. Required amendments and their application rates
                """.trimIndent()

                val content = content {
                    image(reportImage)
                    text(prompt)
                }

                val response = model.generateContent(content)
                val analysis = response.text ?: "Unable to generate analysis"

                withContext(Dispatchers.Main) {
                    resultText.text = formatBoldText(analysis)
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

    private fun extractTextFromPdf(pdfUri: Uri): String {
        val textBuilder = StringBuilder()
        contentResolver.openInputStream(pdfUri)?.use { inputStream ->
            val reader = PdfReader(inputStream)
            for (i in 1..reader.numberOfPages) {
                textBuilder.append(PdfTextExtractor.getTextFromPage(reader, i)).append("\n")
            }
            reader.close()
        }
        return textBuilder.toString()
    }
}
