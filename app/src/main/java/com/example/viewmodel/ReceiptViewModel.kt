package com.example.viewmodel

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.BuildConfig
import com.example.api.Content
import com.example.api.ExtractedReceipt
import com.example.api.GenerateContentRequest
import com.example.api.GenerationConfig
import com.example.api.InlineData
import com.example.api.Part
import com.example.api.RetrofitClient
import com.example.data.AppDatabase
import com.example.data.Receipt
import com.example.data.ReceiptRepository
import com.example.data.User
import com.example.work.WarrantyReminderWorker
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

sealed interface ScanningUiState {
    object Idle : ScanningUiState
    object Scanning : ScanningUiState
    data class ParsedSuccess(val extracted: ExtractedReceipt, val sampleBitmap: Bitmap?) : ScanningUiState
    data class Error(val message: String) : ScanningUiState
}

class ReceiptViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: ReceiptRepository
    private val sharedPrefs = application.getSharedPreferences("vault_auth_prefs", Context.MODE_PRIVATE)

    // Current User Session State
    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: StateFlow<User?> = _currentUser.asStateFlow()

    // Configurable notification settings
    private val _notificationsEnabled = MutableStateFlow(sharedPrefs.getBoolean("notifications_enabled", true))
    val notificationsEnabled = _notificationsEnabled.asStateFlow()

    private val _remind30Days = MutableStateFlow(sharedPrefs.getBoolean("remind_30_days", true))
    val remind30Days = _remind30Days.asStateFlow()

    private val _remind7Days = MutableStateFlow(sharedPrefs.getBoolean("remind_7_days", true))
    val remind7Days = _remind7Days.asStateFlow()

    private val _remind1Day = MutableStateFlow(sharedPrefs.getBoolean("remind_1_day", true))
    val remind1Day = _remind1Day.asStateFlow()

    // Reactively filter receipts dynamically based on logged in user's email
    @OptIn(ExperimentalCoroutinesApi::class)
    val allReceipts: StateFlow<List<Receipt>> = _currentUser
        .flatMapLatest { user ->
            if (user == null) {
                flowOf(emptyList())
            } else {
                repository.getReceiptsForUser(user.email)
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _scanningState = MutableStateFlow<ScanningUiState>(ScanningUiState.Idle)
    val scanningState: StateFlow<ScanningUiState> = _scanningState.asStateFlow()

    private val _customGeminiApiKey = MutableStateFlow(sharedPrefs.getString("custom_gemini_api_key", "") ?: "")
    val customGeminiApiKey: StateFlow<String> = _customGeminiApiKey.asStateFlow()

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    init {
        val database = AppDatabase.getDatabase(application)
        repository = ReceiptRepository(database.receiptDao())

        // Restore user session on launch
        viewModelScope.launch {
            val savedEmail = sharedPrefs.getString("logged_in_email", null)
            if (savedEmail != null) {
                val db = AppDatabase.getDatabase(application)
                val user = db.userDao().getUserByEmail(savedEmail)
                if (user != null) {
                    _currentUser.value = user
                }
            }
        }
    }

    // --- Authentication Actions ---

    private fun hashPassword(password: String): String {
        return try {
            val md = MessageDigest.getInstance("SHA-256")
            val digest = md.digest(password.toByteArray())
            digest.fold("") { str, it -> str + "%02x".format(it) }
        } catch (e: Exception) {
            password // Fallback to plain if exception occurs
        }
    }

    fun signUp(email: String, password: String, fullName: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            try {
                val trimmedEmail = email.trim()
                val trimmedName = fullName.trim()
                if (trimmedEmail.isBlank() || password.isBlank() || trimmedName.isBlank()) {
                    onResult(false, "Please complete all mandatory signup fields.")
                    return@launch
                }
                val db = AppDatabase.getDatabase(getApplication())
                val existing = db.userDao().getUserByEmail(trimmedEmail)
                if (existing != null) {
                    onResult(false, "An account with this email address already exists.")
                    return@launch
                }

                val newUser = User(
                    email = trimmedEmail,
                    passwordHash = hashPassword(password),
                    fullName = trimmedName
                )
                db.userDao().insertUser(newUser)

                // Persist session locally
                sharedPrefs.edit().putString("logged_in_email", trimmedEmail).apply()
                _currentUser.value = newUser
                onResult(true, "Successfully registered!")
            } catch (e: Exception) {
                Log.e("ReceiptViewModel", "Signup failed", e)
                onResult(false, "Registration error: ${e.localizedMessage}")
            }
        }
    }

    fun logIn(email: String, password: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            try {
                val trimmedEmail = email.trim()
                if (trimmedEmail.isBlank() || password.isBlank()) {
                    onResult(false, "Fields cannot be blank.")
                    return@launch
                }
                val db = AppDatabase.getDatabase(getApplication())
                val user = db.userDao().getUserByEmail(trimmedEmail)
                if (user == null) {
                    onResult(false, "Invalid credentials or account does not exist.")
                    return@launch
                }

                if (user.passwordHash == hashPassword(password)) {
                    sharedPrefs.edit().putString("logged_in_email", trimmedEmail).apply()
                    _currentUser.value = user
                    onResult(true, "Successfully signed in!")
                } else {
                    onResult(false, "Incorrect password. Please verify and try again.")
                }
            } catch (e: Exception) {
                Log.e("ReceiptViewModel", "Login failed", e)
                onResult(false, "Login error: ${e.localizedMessage}")
            }
        }
    }

    fun logOut() {
        sharedPrefs.edit().remove("logged_in_email").apply()
        _currentUser.value = null
    }

    // --- Configuration Settings ---

    fun updateNotificationPrefs(enabled: Boolean, days30: Boolean, days7: Boolean, days1: Boolean) {
        sharedPrefs.edit()
            .putBoolean("notifications_enabled", enabled)
            .putBoolean("remind_30_days", days30)
            .putBoolean("remind_7_days", days7)
            .putBoolean("remind_1_day", days1)
            .apply()
        _notificationsEnabled.value = enabled
        _remind30Days.value = days30
        _remind7Days.value = days7
        _remind1Day.value = days1
    }

    fun getGeminiApiKey(): String {
        val customKey = _customGeminiApiKey.value
        if (customKey.isNotBlank()) {
            return customKey
        }
        return BuildConfig.GEMINI_API_KEY
    }

    fun updateCustomGeminiApiKey(key: String) {
        sharedPrefs.edit().putString("custom_gemini_api_key", key).apply()
        _customGeminiApiKey.value = key
    }

    /**
     * Resets the scanning UI state to Idle.
     */
    fun resetScanningState() {
        _scanningState.value = ScanningUiState.Idle
    }

    /**
     * Contrast & brightness boost pre-processing to clarify folded or low-contrast paper tickets.
     */
    private fun preprocessImageForOcr(src: Bitmap): Bitmap {
        return try {
            val contrast = 1.35f
            val brightness = 8.0f
            
            val cm = android.graphics.ColorMatrix(floatArrayOf(
                contrast, 0f, 0f, 0f, brightness,
                0f, contrast, 0f, 0f, brightness,
                0f, 0f, contrast, 0f, brightness,
                0f, 0f, 0f, 1f, 0f
            ))
            
            val output = Bitmap.createBitmap(src.width, src.height, src.config ?: Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(output)
            val paint = android.graphics.Paint().apply {
                isAntiAlias = true
                isFilterBitmap = true
            }
            paint.colorFilter = android.graphics.ColorMatrixColorFilter(cm)
            canvas.drawBitmap(src, 0f, 0f, paint)
            output
        } catch (e: Exception) {
            src
        }
    }

    private fun Bitmap.toBase64(): String {
        val outputStream = ByteArrayOutputStream()
        compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
        return Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
    }

    /**
     * Sends a preprocessed image to Gemini 3.5 Flash using high-accuracy system prompts & few-shot examples.
     */
    fun scanReceiptImage(bitmap: Bitmap) {
        viewModelScope.launch {
            _scanningState.value = ScanningUiState.Scanning
            try {
                val apiKey = getGeminiApiKey()
                if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
                    _scanningState.value = ScanningUiState.Error(
                        "Please configure your Gemini API Key in Settings (or Secrets in AI Studio)."
                    )
                    return@launch
                }

                // Preprocess the input image to sharpen text details
                val preprocessed = withContext(Dispatchers.Default) {
                    preprocessImageForOcr(bitmap)
                }

                val base64Image = withContext(Dispatchers.Default) {
                    preprocessed.toBase64()
                }

                // Refined System Instruction incorporating few-shot examples
                val systemPrompt = """
                    You are an elite, highly precise OCR and receipt metadata extraction engine. Your job is to analyze any receipt image, resolve noisy elements, clean up deskewed or low-contrast text, scan for dates, merchant identity, totals, currency, bought items, and deduce warranty characteristics with maximum consistency.

                    We support dynamic warranties. Deduce 'hasWarranty' = true if any core item purchased carries high-value protection (electronics, laptops, headphones, machinery, premium beds, tools, appliances). If possible, calculate the 'warrantyExpiryDate' precisely or fallback to 12 months after the purchase date.

                    IMPORTANT FEW-SHOT EXAMPLES FOR FORMATTING CONSISTENCY:
                    Example 1 Input: Image of "Best Buy" dated Oct 12, 2024 for "UN65TU7000FXZA Smart TV" costing "$549.99" tax included USD. Support toll: 1-800-433-5778.
                    Example 1 Output (Raw JSON):
                    {
                      "merchantName": "Best Buy",
                      "purchaseDate": "2024-10-12",
                      "totalAmount": 549.99,
                      "currency": "USD",
                      "itemsList": "UN65TU7000FXZA TV, Protection Plan",
                      "hasWarranty": true,
                      "warrantyExpiryDate": "2025-10-12",
                      "supportContact": "1-800-433-5778"
                    }

                    Example 2 Input: Image of "IKEA Brooklyn" on 1/15/2025 showing "MARKUS Chair" costing "USD 229.00".
                    Example 2 Output (Raw JSON):
                    {
                      "merchantName": "IKEA",
                      "purchaseDate": "2025-01-15",
                      "totalAmount": 229.00,
                      "currency": "USD",
                      "itemsList": "MARKUS Office Chair",
                      "hasWarranty": true,
                      "warrantyExpiryDate": "2026-01-15",
                      "supportContact": ""
                    }

                    Only return a single, minified, valid raw JSON object matching the requested schema. Never surround with markdown accents like ```json or prefix with commentary.
                """.trimIndent()

                // Refined User Prompt mapping schema clearly
                val userPrompt = """
                    Perform OCR extraction and structured deduction on this receipt. Be extremely careful about decimal numbers, dates, and special characters. Return a clean, single-level JSON object with the following schema:
                    {
                      "merchantName": "Name of the merchant",
                      "purchaseDate": "YYYY-MM-DD format (infer year correctly)",
                      "totalAmount": 0.00,
                      "currency": "3-letter currency code, e.g., USD, EUR, INR",
                      "itemsList": "A clean, comma-separated list of items",
                      "hasWarranty": true/false (True for electronics, appliances, furniture, and major gadgets, else false),
                      "warrantyExpiryDate": "YYYY-MM-DD (typically 1 year from purchaseDate for warranted items, null or blank if hasWarranty is false)",
                      "supportContact": "Phone number, email, or URL if visible, else blank"
                    }
                    Fill in all fields. Keep it strictly compliant to the JSON specification.
                """.trimIndent()

                val request = GenerateContentRequest(
                    contents = listOf(
                        Content(
                            parts = listOf(
                                Part(text = userPrompt),
                                Part(inlineData = InlineData(mimeType = "image/jpeg", data = base64Image))
                            )
                        )
                    ),
                    generationConfig = GenerationConfig(
                        responseMimeType = "application/json",
                        temperature = 0.15f
                    ),
                    systemInstruction = Content(
                        parts = listOf(Part(text = systemPrompt))
                    )
                )

                val response = withContext(Dispatchers.IO) {
                    RetrofitClient.service.generateContent(apiKey, request)
                }

                val jsonResponseText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                if (jsonResponseText == null) {
                    _scanningState.value = ScanningUiState.Error("No data returned from Gemini.")
                    return@launch
                }

                Log.d("ReceiptViewModel", "Raw Gemini JSON: $jsonResponseText")

                val cleanJson = jsonResponseText.trim()
                    .removePrefix("```json")
                    .removeSuffix("```")
                    .trim()

                val adapter = moshi.adapter(ExtractedReceipt::class.java)
                val extracted = withContext(Dispatchers.Default) {
                    adapter.fromJson(cleanJson)
                }

                if (extracted != null) {
                    _scanningState.value = ScanningUiState.ParsedSuccess(extracted, bitmap)
                } else {
                    _scanningState.value = ScanningUiState.Error("Failed to parse the Gemini JSON response.")
                }

            } catch (e: Exception) {
                Log.e("ReceiptViewModel", "Scanning failed", e)
                _scanningState.value = ScanningUiState.Error("OCR Error: ${e.localizedMessage ?: "Unknown error"}")
            }
        }
    }

    fun calculateExpiryDate(purchaseDateStr: String, durationMonths: Int): String {
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val date = sdf.parse(purchaseDateStr) ?: Date()
            val calendar = Calendar.getInstance()
            calendar.time = date
            calendar.add(Calendar.MONTH, durationMonths)
            sdf.format(calendar.time)
        } catch (e: Exception) {
            val calendar = Calendar.getInstance()
            calendar.time = Date()
            calendar.add(Calendar.MONTH, 12)
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            sdf.format(calendar.time)
        }
    }

    /**
     * Saves the parsed and finalized receipt record associated with the active user email partition,
     * then schedules multi-threshold reminders.
     */
    fun saveReceipt(
        merchantName: String,
        purchaseDate: String,
        totalAmount: Double,
        currency: String,
        itemsList: String,
        hasWarranty: Boolean,
        warrantyDurationMonths: Int,
        supportContact: String,
        explicitExpiryDate: String = ""
    ) {
        viewModelScope.launch {
            try {
                val expiryDate = if (hasWarranty) {
                    if (explicitExpiryDate.isNotBlank()) {
                        explicitExpiryDate.trim()
                    } else if (warrantyDurationMonths > 0) {
                        calculateExpiryDate(purchaseDate, warrantyDurationMonths)
                    } else {
                        ""
                    }
                } else {
                    ""
                }

                val activeUserEmail = _currentUser.value?.email ?: "default_user"

                val receipt = Receipt(
                    merchantName = merchantName.ifBlank { "Unknown Merchant" },
                    purchaseDate = purchaseDate.ifBlank {
                        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                    },
                    totalAmount = totalAmount,
                    currency = currency.ifBlank { "USD" },
                    itemsList = itemsList,
                    hasWarranty = hasWarranty,
                    warrantyExpiryDate = expiryDate,
                    supportContact = supportContact,
                    userId = activeUserEmail
                )

                // 1. Insert to Room
                val savedId = withContext(Dispatchers.IO) {
                    repository.insertReceipt(receipt)
                }

                // 2. Schedule multi-threshold reminders (30 days, 7 days, 1 day)
                if (hasWarranty && expiryDate.isNotBlank()) {
                    scheduleWarrantyReminder(savedId, expiryDate, merchantName, 30)
                    scheduleWarrantyReminder(savedId, expiryDate, merchantName, 7)
                    scheduleWarrantyReminder(savedId, expiryDate, merchantName, 1)
                }

                _scanningState.value = ScanningUiState.Idle
            } catch (e: Exception) {
                Log.e("ReceiptViewModel", "Failed to save receipt", e)
                _scanningState.value = ScanningUiState.Error("Database Error: ${e.localizedMessage}")
            }
        }
    }

    private fun calculateDelayMs(expiryDateStr: String, thresholdDays: Int): Long {
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val expiryDate = sdf.parse(expiryDateStr) ?: return 0L
            
            val calendar = Calendar.getInstance()
            calendar.time = expiryDate
            calendar.add(Calendar.DAY_OF_YEAR, -thresholdDays)
            val notificationTimeMs = calendar.timeInMillis
            val currentTimeMs = System.currentTimeMillis()

            val delay = notificationTimeMs - currentTimeMs
            if (delay > 0) delay else 5000L // Trigger in 5 seconds if date has already elapsed
        } catch (e: Exception) {
            0L
        }
    }

    private fun scheduleWarrantyReminder(receiptId: Long, expiryDate: String, merchantName: String, thresholdDays: Int) {
        val delayMs = calculateDelayMs(expiryDate, thresholdDays)
        
        val inputData = Data.Builder()
            .putLong("receipt_id", receiptId)
            .putString("merchant_name", merchantName)
            .putInt("threshold_days", thresholdDays)
            .build()

        val workRequest = OneTimeWorkRequestBuilder<WarrantyReminderWorker>()
            .setInputData(inputData)
            .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
            .addTag("reminder_${receiptId}_$thresholdDays")
            .build()

        WorkManager.getInstance(getApplication()).enqueue(workRequest)
        Log.d("ReceiptViewModel", "Scheduled reminder ($thresholdDays days) for receipt $receiptId with delay of ${delayMs / 1000}s")
    }

    fun deleteReceipt(receipt: Receipt) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteReceipt(receipt)
        }
    }

    /**
     * Attempts to fetch raw text content of product page, then prompts Gemini to extract the warranty length in months.
     */
    suspend fun extractWarrantyDurationFromUrl(url: String): Int? {
        return withContext(Dispatchers.IO) {
            try {
                val cleanUrl = if (!url.startsWith("http://") && !url.startsWith("https://")) {
                    "https://$url"
                } else {
                    url
                }

                // 1. Fetch webpage text content safely with high compatibility
                val connection = java.net.URL(cleanUrl).openConnection() as java.net.HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 6000
                connection.readTimeout = 6000
                connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/437.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/437.36")
                connection.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")
                
                val rawHtml = connection.inputStream.bufferedReader().use { it.readText() }
                
                // Keep only alphanumeric characters and core punctuation in a truncated form to minimize token payload
                val cleanText = rawHtml
                    .replace(Regex("<script[^>]*?>[\\s\\S]*?</script>"), " ")
                    .replace(Regex("<style[^>]*?>[\\s\\S]*?</style>"), " ")
                    .replace(Regex("<[^>]*>"), " ")
                    .replace(Regex("\\s+"), " ")
                    .trim()
                    .take(8000)

                if (cleanText.isBlank()) return@withContext null

                // 2. Formulate Gemini AI query
                val apiKey = getGeminiApiKey()
                if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
                    return@withContext null
                }

                val systemPrompt = """
                    You are an expert product protection and warranty crawler. Analyze the provided truncated product page text, find any mentions of warranties, guarantees, manufacturer coverage, protection period, or product support plans, and state the warranty duration strictly in INTEGER MONTHS.
                    
                    Respond ONLY with a minified, valid JSON object following this exact schema:
                    {"warranty_duration_months": 12}
                    
                    - If the page specifies "1 Year Warranty", return {"warranty_duration_months": 12}.
                    - If it specifies "2 Years Manufacturer Warranty", return {"warranty_duration_months": 24}.
                    - If no warranty period is mentioned, return {"warranty_duration_months": null}.
                    - Never return markdown blocks, accents, backticks, or other text wrapper formats.
                """.trimIndent()

                val userPrompt = """
                    Extract the support warranty coverage period from this product page content:
                    
                    $cleanText
                """.trimIndent()

                val request = GenerateContentRequest(
                    contents = listOf(
                        Content(
                            parts = listOf(Part(text = userPrompt))
                        )
                    ),
                    generationConfig = GenerationConfig(
                        responseMimeType = "application/json",
                        temperature = 0.1f
                    ),
                    systemInstruction = Content(
                        parts = listOf(Part(text = systemPrompt))
                    )
                )

                val response = RetrofitClient.service.generateContent(apiKey, request)
                val jsonText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                if (jsonText != null) {
                    val cleanedJson = jsonText.trim()
                        .removePrefix("```json")
                        .removeSuffix("```")
                        .trim()
                    
                    // Simple manual matching to avoid complex parsing errors
                    val matchResult = Regex("\"warranty_duration_months\"\\s*:\\s*(\\d+)").find(cleanedJson)
                    matchResult?.groupValues?.get(1)?.toIntOrNull()
                } else {
                    null
                }
            } catch (e: Exception) {
                Log.e("ReceiptViewModel", "Failed to extract warranty from url: ${e.localizedMessage}", e)
                null
            }
        }
    }
}
