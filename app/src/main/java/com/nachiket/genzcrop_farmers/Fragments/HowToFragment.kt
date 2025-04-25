package com.nachiket.genzcrop_farmers.Fragments

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.nachiket.genzcrop_farmers.Adapters.ChatAdapter
import com.nachiket.genzcrop_farmers.Internal_Activities.ProfileActivity
import com.nachiket.genzcrop_farmers.R
import com.nachiket.genzcrop_farmers.data_class.ChatMessage
import com.nachiket.genzcrop_farmers.databinding.FragmentHowToBinding
import kotlinx.coroutines.launch

class HowToFragment : Fragment() {

    private lateinit var binding: FragmentHowToBinding
    private lateinit var chatAdapter: ChatAdapter
    private val chatMessages = mutableListOf<ChatMessage>()
    private lateinit var generativeModel: GenerativeModel
    private val conversationHistory = mutableListOf<ChatMessage>()
    private val initialContext = """
    GenZCrop App Context:
    GenZCrop is a revolutionary platform designed to empower farmers by enabling direct crop sales to consumers, maximizing profits while ensuring quality produce. The app consists of four key sections:
    Listed Crops:
    
    Farmers can browse all available crops listed for sale.
    If a farmer wants to sell their crop, they can click the "+" icon and fill in the necessary details.
    Crop Registration & AI Grading:
    
    Farmers can register their crops by providing details and uploading three images.
    An AI-based grading system will analyze the uploaded images to assess crop quality and assign a grade (A+, A, B+, B, C).
    The AI also provides a remark/analysis of the crop’s condition.
    Once graded, the crop is listed for direct sale to consumers, bypassing middlemen.
    Orders:
    
    This section allows farmers to view and manage their incoming orders from buyers.
    AI Crop & Soil Health Analysis:
    
    Farmers can check the health of their crops using AI by uploading images.
    They can also analyze their soil health using:
    Location-based analysis
    Image-based analysis
    Soil report submission (most accurate method)
    This helps farmers achieve the best possible harvest by optimizing their farming practices.
    now greet them and explain them about the app in brief
    """.trimIndent()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentHowToBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize Gemini Flash 2.0
        generativeModel = GenerativeModel(
            modelName = "gemini-2.0-flash",
            apiKey = "//API KEY"
        )

        // Set up UI
        setupLanguageSpinner()
        setupChatRecyclerView()
        setupSendButton()

        // Handle profile and verification icons
        binding.profileicon.setOnClickListener {
            val intent = Intent(requireContext(), ProfileActivity::class.java)
            startActivity(intent)
        }

        binding.verificationIcon.setOnClickListener {
            Toast.makeText(requireContext(), "Verification Clicked", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupLanguageSpinner() {
        val languages = arrayOf("English", "मराठी", "हिंदी", "தமிழ்", "తెలుగు", "Español", "Français", "Französisch")
        val adapter = ArrayAdapter(requireContext(), R.layout.custom_spinner_item, languages)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.languageSpinner.adapter = adapter

        binding.languageSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selectedLanguage = languages[position]
                Toast.makeText(requireContext(), "Selected: $selectedLanguage", Toast.LENGTH_SHORT).show()

                // Clear the chat
                chatMessages.clear()
                chatAdapter.notifyDataSetChanged()

                // Reinitialize the context
                val contextMessage = initialContext.replace("[LANGUAGE]", selectedLanguage)
                getGeminiResponse(contextMessage, selectedLanguage, isInitialContext = true)
            }

            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }
    }

    private fun setupChatRecyclerView() {
        chatAdapter = ChatAdapter(chatMessages)
        binding.chatRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = chatAdapter
        }
    }

    private fun setupSendButton() {
        binding.sendButton.setOnClickListener {
            val userMessage = binding.chatInput.text.toString().trim()
            if (userMessage.isNotEmpty()) {
                // Add user message to chat
                chatMessages.add(ChatMessage(userMessage, true)) // true for user message
                chatAdapter.notifyItemInserted(chatMessages.size - 1)
                binding.chatRecyclerView.smoothScrollToPosition(chatMessages.size - 1)
                binding.chatInput.text.clear()

                // Get Gemini response
                val selectedLanguage = binding.languageSpinner.selectedItem as String
                getGeminiResponse(userMessage, selectedLanguage)
            }
        }
    }

    private fun getGeminiResponse(userMessage: String, selectedLanguage: String, isInitialContext: Boolean = false) {
        // Show loading indicator only for user messages
        if (!isInitialContext) {
            chatMessages.add(ChatMessage("Typing...", false)) // false for bot message
            chatAdapter.notifyItemInserted(chatMessages.size - 1)
            binding.chatRecyclerView.smoothScrollToPosition(chatMessages.size - 1)
        }

        // Generate response using Gemini
        lifecycleScope.launch {
            try {
                // Construct the prompt with the context and conversation history
                val prompt = buildPrompt(userMessage, selectedLanguage, isInitialContext)

                val content = content {
                    text(prompt)
                }
                val response = generativeModel.generateContent(content)
                val botMessage = response.text ?: "Sorry, I couldn't generate a response."

                // Update chat with Gemini's response
                if (!isInitialContext) {
                    chatMessages.removeAt(chatMessages.size - 1) // Remove "Typing..."
                    chatMessages.add(ChatMessage(botMessage, false))
                    conversationHistory.add(ChatMessage(userMessage, true)) // Add user message to history
                    conversationHistory.add(ChatMessage(botMessage, false)) // Add bot message to history
                    chatAdapter.notifyDataSetChanged()
                    binding.chatRecyclerView.smoothScrollToPosition(chatMessages.size - 1)
                }
            } catch (e: Exception) {
                if (!isInitialContext) {
                    chatMessages.removeAt(chatMessages.size - 1) // Remove "Typing..."
                    chatMessages.add(ChatMessage("Error: ${e.message}", false))
                    chatAdapter.notifyDataSetChanged()
                    binding.chatRecyclerView.smoothScrollToPosition(chatMessages.size - 1)
                }
            }
        }
    }

    private fun buildPrompt(userMessage: String, selectedLanguage: String, isInitialContext: Boolean): String {
        return if (isInitialContext) {
            // Include the context only once at the start
            """
            $initialContext
            
            Respond in $selectedLanguage: Greet the user and briefly explain the app.
        """.trimIndent()
        } else {
            // For subsequent messages, use the conversation history
            val history = conversationHistory.joinToString("\n") { msg ->
                if (msg.isUser) "User: ${msg.text}" else "AI: ${msg.text}"
            }
            """
            $initialContext
            
            $history
            
            Respond in $selectedLanguage: $userMessage
            Use *emphasis* and **headers** where appropriate.
        """.trimIndent()
        }
    }
}
