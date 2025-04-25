package com.nachiket.genzcrop_farmers.Internal_Activities

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.nachiket.genzcrop_farmers.R
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import kotlinx.coroutines.*
import okhttp3.*
import org.json.JSONObject
import java.io.IOException
import android.text.Spannable
import android.text.SpannableString
import android.text.style.StyleSpan
import android.graphics.Typeface
import android.util.Log
import java.util.regex.Pattern

class LocationAnalysisActivity : AppCompatActivity() {

    private lateinit var imageView: ImageView
    private lateinit var resultText: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var model: GenerativeModel
    private var pincode: String = ""
    private val client = OkHttpClient()

    companion object {
        private const val CAMERA_REQUEST_CODE = 105
        private const val GALLERY_REQUEST_CODE = 106
        private const val GEMINI_API_KEY = "AIzaSyCUTqJVg1f9MxrfY5x46jUn36eKst_sBCQ"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_location_analysis)

        imageView = findViewById(R.id.imageView)
        resultText = findViewById(R.id.resultText)
        progressBar = findViewById(R.id.progressBar)

        model = GenerativeModel("gemini-2.0-flash", GEMINI_API_KEY)

        showPincodeInputDialog()
    }

    private fun showPincodeInputDialog() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 20, 50, 20)
        }

        val input = EditText(this).apply {
            hint = "Enter PIN Code"
            setSingleLine()
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            maxLines = 1
            filters = arrayOf(android.text.InputFilter.LengthFilter(6))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        layout.addView(input)

        AlertDialog.Builder(this).apply {
            setTitle("Enter PIN Code")
            setMessage("Please enter a valid 6-digit Indian PIN code")
            setView(layout)
            setCancelable(false)
            setPositiveButton("Continue") { _, _ ->
                val enteredPincode = input.text.toString().trim()
                if (isValidPincode(enteredPincode)) {
                    pincode = enteredPincode
                    getCoordinatesFromPincode(pincode)
                } else {
                    Toast.makeText(
                        this@LocationAnalysisActivity,
                        "Please enter a valid 6-digit PIN code starting with 1-9",
                        Toast.LENGTH_LONG
                    ).show()
                    showPincodeInputDialog()
                }
            }
            setNegativeButton("Cancel") { _, _ ->
                finish()
            }
            show()
        }
    }

    private fun getCoordinatesFromNominatim(district: String, state: String, pincode: String) {
        val encodedAddress =
            java.net.URLEncoder.encode("$district, $state, India, $pincode", "UTF-8")
        val nominatimUrl =
            "https://nominatim.openstreetmap.org/search?q=$encodedAddress&format=json&limit=1"

        val request = Request.Builder()
            .url(nominatimUrl)
            .addHeader("User-Agent", "GenZCrop Android App") // Required by Nominatim
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    Toast.makeText(
                        this@LocationAnalysisActivity,
                        "Error fetching coordinates: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                    progressBar.visibility = View.GONE
                }
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    val jsonString = response.body?.string()
                    val jsonArray = org.json.JSONArray(jsonString)

                    if (jsonArray.length() == 0) {
                        runOnUiThread {
                            Toast.makeText(
                                this@LocationAnalysisActivity,
                                "Could not find coordinates for this location",
                                Toast.LENGTH_LONG
                            ).show()
                            progressBar.visibility = View.GONE
                        }
                        return
                    }

                    val location = jsonArray.getJSONObject(0)
                    val latitude = location.getString("lat")
                    val longitude = location.getString("lon")

                    // Log the coordinates
                    Log.d("Nominatim", "Lat: $latitude, Lon: $longitude")

                    // Now get the soil data
                    getSoilData(latitude, longitude)

                } catch (e: Exception) {
                    Log.e("Nominatim", "Error parsing response: ${e.message}", e)
                    runOnUiThread {
                        Toast.makeText(
                            this@LocationAnalysisActivity,
                            "Error getting coordinates: ${e.message}",
                            Toast.LENGTH_LONG
                        ).show()
                        progressBar.visibility = View.GONE
                    }
                }
            }
        })
    }

    private fun getCoordinatesFromPincode(pincode: String) {
        progressBar.visibility = View.VISIBLE
        resultText.text = "Fetching location data for PIN code: $pincode"

        // First validate PIN code using India Post API
        val postUrl = "https://api.postalpincode.in/pincode/$pincode"

        val postRequest = Request.Builder()
            .url(postUrl)
            .build()

        client.newCall(postRequest).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    Toast.makeText(
                        this@LocationAnalysisActivity,
                        "Network error: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                    progressBar.visibility = View.GONE
                    showPincodeInputDialog()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    val jsonString = response.body?.string()
                    if (jsonString == null) {
                        runOnUiThread {
                            Toast.makeText(
                                this@LocationAnalysisActivity,
                                "Empty response from server",
                                Toast.LENGTH_LONG
                            ).show()
                            progressBar.visibility = View.GONE
                            showPincodeInputDialog()
                        }
                        return
                    }

                    val jsonArray = org.json.JSONArray(jsonString)
                    val firstObject = jsonArray.getJSONObject(0)

                    if (firstObject.getString("Status") != "Success") {
                        runOnUiThread {
                            Toast.makeText(
                                this@LocationAnalysisActivity,
                                "Invalid PIN code",
                                Toast.LENGTH_LONG
                            ).show()
                            progressBar.visibility = View.GONE
                            showPincodeInputDialog()
                        }
                        return
                    }

                    val postOfficeArray = firstObject.getJSONArray("PostOffice")
                    if (postOfficeArray.length() == 0) {
                        runOnUiThread {
                            Toast.makeText(
                                this@LocationAnalysisActivity,
                                "No data found for this PIN code",
                                Toast.LENGTH_LONG
                            ).show()
                            progressBar.visibility = View.GONE
                            showPincodeInputDialog()
                        }
                        return
                    }

                    // Get the first post office details
                    val postOffice = postOfficeArray.getJSONObject(0)
                    val district = postOffice.getString("District")
                    val state = postOffice.getString("State")

                    // Use OpenStreetMap Nominatim API to get coordinates
                    getCoordinatesFromNominatim(district, state, pincode)

                } catch (e: Exception) {
                    Log.e("PinCodeAPI", "Error parsing response: ${e.message}", e)
                    runOnUiThread {
                        Toast.makeText(
                            this@LocationAnalysisActivity,
                            "Error processing PIN code data: ${e.message}",
                            Toast.LENGTH_LONG
                        ).show()
                        progressBar.visibility = View.GONE
                        showPincodeInputDialog()
                    }
                }
            }
        })
    }

    private fun isValidPincode(pincode: String): Boolean {
        // Indian PIN codes are 6 digits and don't start with 0
        return pincode.matches(Regex("^[1-9][0-9]{5}$"))
    }

    private fun getSoilData(latitude: String, longitude: String) {
        progressBar.visibility = View.VISIBLE
        resultText.text = "Fetching soil data..."

        // Create client with longer timeout
        val customClient = OkHttpClient.Builder()
            .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .build()

        // ISRIC SoilGrids API endpoint
        val url = "https://rest.isric.org/soilgrids/v2.0/properties/query?lat=$latitude&lon=$longitude"

        val request = Request.Builder()
            .url(url)
            .addHeader("Accept", "application/json")
            .build()

        customClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    Log.e("SoilAPI", "Network error: ${e.message}", e)
                    Toast.makeText(
                        this@LocationAnalysisActivity,
                        "Couldn't connect to soil database. Using default values.",
                        Toast.LENGTH_LONG
                    ).show()
                    showChoiceDialog(getDefaultSoilValues())
                }
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    val jsonString = response.body?.string()

                    if (!response.isSuccessful || jsonString == null || jsonString.isEmpty()) {
                        Log.w("SoilAPI", "Empty or error response: ${response.code}")
                        runOnUiThread {
                            Toast.makeText(
                                this@LocationAnalysisActivity,
                                "No soil data available for this location. Using default values.",
                                Toast.LENGTH_LONG
                            ).show()
                            showChoiceDialog(getDefaultSoilValues())
                        }
                        return
                    }

                    val soilData = JSONObject(jsonString)
                    val extractedSoilData = extractSoilProperties(soilData)

                    runOnUiThread {
                        progressBar.visibility = View.GONE
                        showChoiceDialog(extractedSoilData)
                    }

                } catch (e: Exception) {
                    Log.e("SoilAPI", "Error parsing soil data: ${e.message}", e)
                    runOnUiThread {
                        Toast.makeText(
                            this@LocationAnalysisActivity,
                            "Error processing soil data. Using default values.",
                            Toast.LENGTH_LONG
                        ).show()
                        progressBar.visibility = View.GONE
                        showChoiceDialog(getDefaultSoilValues())
                    }
                }
            }
        })
    }

    private fun extractSoilProperties(soilData: JSONObject): JSONObject {
        val result = JSONObject()

        try {
            if (!soilData.has("properties") || soilData.isNull("properties")) {
                // Return default values if no properties found
                return getDefaultSoilValues()
            }

            val properties = soilData.getJSONObject("properties")
            val layers = properties.optJSONArray("layers")

            if (layers == null || layers.length() == 0) {
                return getDefaultSoilValues()
            }

            // Extract clay content
            val clayLayer = findLayerByName(layers, "clay")
            val clayValue = extractValueFromLayer(clayLayer, "0-5cm")
            if (clayValue != null) result.put("clay", clayValue)

            // Extract silt content
            val siltLayer = findLayerByName(layers, "silt")
            val siltValue = extractValueFromLayer(siltLayer, "0-5cm")
            if (siltValue != null) result.put("silt", siltValue)

            // Extract sand content
            val sandLayer = findLayerByName(layers, "sand")
            val sandValue = extractValueFromLayer(sandLayer, "0-5cm")
            if (sandValue != null) result.put("sand", sandValue)

            // Extract pH
            val phLayer = findLayerByName(layers, "phh2o")
            val phValue = extractValueFromLayer(phLayer, "0-5cm")
            if (phValue != null) result.put("ph", phValue / 10.0) // Convert to standard pH scale

            // Extract organic carbon
            val socLayer = findLayerByName(layers, "soc")
            val socValue = extractValueFromLayer(socLayer, "0-5cm")
            if (socValue != null) result.put("organic_carbon", socValue / 10.0) // Convert to percentage

            // If any essential property is missing, fill with default
            if (!result.has("clay")) result.put("clay", 20.0)
            if (!result.has("silt")) result.put("silt", 40.0)
            if (!result.has("sand")) result.put("sand", 40.0)
            if (!result.has("ph")) result.put("ph", 6.5)
            if (!result.has("organic_carbon")) result.put("organic_carbon", 1.5)

            return result

        } catch (e: Exception) {
            Log.e("SoilExtract", "Error extracting soil properties: ${e.message}", e)
            return getDefaultSoilValues()
        }
    }

    private fun extractValueFromLayer(layer: JSONObject?, depthLabel: String): Double? {
        if (layer == null) return null

        try {
            val depths = layer.optJSONArray("depths") ?: return null

            for (i in 0 until depths.length()) {
                val depth = depths.getJSONObject(i)
                if (depth.optString("label") == depthLabel) {
                    val values = depth.optJSONObject("values") ?: return null
                    // Try mean first, then median (Q0.5)
                    val mean = values.optDouble("mean")
                    if (!values.isNull("mean") && mean != 0.0) {
                        return mean
                    }

                    val median = values.optDouble("Q0.5")
                    if (!values.isNull("Q0.5") && median != 0.0) {
                        return median
                    }

                    return null
                }
            }
            return null
        } catch (e: Exception) {
            Log.e("ExtractValue", "Error extracting value: ${e.message}", e)
            return null
        }
    }

    private fun getDefaultSoilValues(): JSONObject {
        return JSONObject().apply {
            put("clay", 20.0)
            put("silt", 40.0)
            put("sand", 40.0)
            put("ph", 6.5)
            put("organic_carbon", 1.5)
        }
    }

    private fun findLayerByName(layers: org.json.JSONArray, name: String): JSONObject? {
        for (i in 0 until layers.length()) {
            val layer = layers.getJSONObject(i)
            if (layer.optString("name") == name) {
                return layer
            }
        }
        return null
    }

    private fun showChoiceDialog(soilData: JSONObject) {
        AlertDialog.Builder(this).apply {
            setTitle("Choose Soil Image Source")
            setMessage("How would you like to provide the soil image?")
            setCancelable(false)
            setPositiveButton("Take Photo") { _, _ ->
                Intent(MediaStore.ACTION_IMAGE_CAPTURE).also { intent ->
                    intent.putExtra("soilData", soilData.toString())
                    startActivityForResult(intent, CAMERA_REQUEST_CODE)
                }
            }
            setNegativeButton("Upload Image") { _, _ ->
                Intent(
                    Intent.ACTION_PICK,
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                ).also { intent ->
                    intent.putExtra("soilData", soilData.toString())
                    startActivityForResult(intent, GALLERY_REQUEST_CODE)
                }
            }
            show()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (resultCode == RESULT_OK && data != null) {
            try {
                val imageBitmap: Bitmap? = when (requestCode) {
                    CAMERA_REQUEST_CODE -> data.extras?.get("data") as Bitmap
                    GALLERY_REQUEST_CODE -> {
                        val uri = data.data
                        MediaStore.Images.Media.getBitmap(contentResolver, uri)
                    }

                    else -> null
                }
                imageBitmap?.let {
                    imageView.setImageBitmap(it)
                    val soilData = data.getStringExtra("soilData")
                    analyzeLocationSoil(it, JSONObject(soilData ?: "{}"))
                }
            } catch (e: Exception) {
                Toast.makeText(this, "Error processing image", Toast.LENGTH_SHORT).show()
                showChoiceDialog(JSONObject())
            }
        } else {
            showChoiceDialog(JSONObject())
        }
    }

    private fun formatBoldText(text: String): CharSequence {
        val pattern = Pattern.compile("\\*(.*?)\\*")
        val matcher = pattern.matcher(text)
        val spannableString = SpannableString(text)

        while (matcher.find()) {
            spannableString.setSpan(
                StyleSpan(Typeface.BOLD),
                matcher.start(),
                matcher.end(),
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }

        return spannableString.toString().replace("*", "")
    }

    private fun analyzeLocationSoil(soilImage: Bitmap, soilData: JSONObject) {
        progressBar.visibility = View.VISIBLE
        resultText.text = "Analyzing soil from image and location data..."

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Extract soil properties with fallback values
                val clay = soilData.optDouble("clay", 20.0)
                val silt = soilData.optDouble("silt", 40.0)
                val sand = soilData.optDouble("sand", 40.0)
                val ph = soilData.optDouble("ph", 6.5)
                val organicCarbon = soilData.optDouble("organic_carbon", 1.5)

                val prompt = """
                    Analyze this soil image along with the following soil data from PIN code *$pincode*:
                    
                    Location Soil Data:
                    - Clay content: $clay%
                    - Silt content: $silt%
                    - Sand content: $sand%
                    - pH level: $ph
                    - Organic Carbon: ${organicCarbon}%
                    
                    Based on both the image and soil composition data, please provide:
                    1. *Soil Type Classification*: Determine the soil texture class and structure
                    2. *Nutrient Analysis*: Interpret nutrient availability based on pH and organic matter
                    3. *Crop Recommendations*: List suitable crops for this soil type
                    4. *Soil Management*: Suggest specific improvements needed
                    5. *Water Management*: Recommend irrigation practices based on soil texture
                    
                    If the image does not appear to be of soil, request a valid image.
                    """.trimIndent()

                val content = content {
                    image(soilImage)
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
}