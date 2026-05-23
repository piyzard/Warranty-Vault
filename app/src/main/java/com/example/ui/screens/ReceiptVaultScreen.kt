package com.example.ui.screens

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.api.ExtractedReceipt
import com.example.data.Receipt
import com.example.data.User
import com.example.viewmodel.ReceiptViewModel
import com.example.viewmodel.ScanningUiState
import com.example.ui.theme.ExpiringBackground
import com.example.ui.theme.ExpiringText
import com.example.ui.theme.ExpiringBadgeBg
import com.example.ui.theme.ExpiringBadgeText
import com.example.ui.theme.ActiveBadgeBg
import com.example.ui.theme.ActiveBadgeText
import com.example.ui.theme.SoftAlertBg
import com.example.ui.theme.SoftAlertText
import com.example.ui.theme.CompactActiveBg
import com.example.ui.theme.CompactActiveText
import com.example.ui.theme.MinimalTextFieldContainer
import com.example.ui.theme.MinimalTextFieldText
import com.example.ui.theme.PlainSecondaryAccent
import android.content.Context
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.layout.ContentScale
import coil.compose.rememberAsyncImagePainter
import android.net.Uri
import androidx.compose.ui.draw.scale
import java.io.InputStream
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.Log
import android.widget.Toast
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiptVaultScreen(
    viewModel: ReceiptViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val receipts by viewModel.allReceipts.collectAsStateWithLifecycle()
    val scanningState by viewModel.scanningState.collectAsStateWithLifecycle()

    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    val notificationsEnabled by viewModel.notificationsEnabled.collectAsStateWithLifecycle()
    val remind30Days by viewModel.remind30Days.collectAsStateWithLifecycle()
    val remind7Days by viewModel.remind7Days.collectAsStateWithLifecycle()
    val remind1Day by viewModel.remind1Day.collectAsStateWithLifecycle()

    var showAccountDialog by remember { mutableStateOf(false) }

    var searchQuery by remember { mutableStateOf("") }
    var selectedReceiptForDetail by remember { mutableStateOf<Receipt?>(null) }
    var showScanSelector by remember { mutableStateOf(false) }

    // Modern photo picker for actual gallery images
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let {
            try {
                val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                if (bitmap != null) {
                    viewModel.scanReceiptImage(bitmap)
                }
            } catch (e: Exception) {
                // handle error
            }
        }
    }

    // Camera launcher
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        bitmap?.let {
            viewModel.scanReceiptImage(it)
        }
    }

    // PDF document launcher
    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            try {
                val pfd: ParcelFileDescriptor? = context.contentResolver.openFileDescriptor(uri, "r")
                if (pfd != null) {
                    val renderer = PdfRenderer(pfd)
                    if (renderer.pageCount > 0) {
                        val page = renderer.openPage(0)
                        val width = page.width * 2
                        val height = page.height * 2
                        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                        bitmap.eraseColor(android.graphics.Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        page.close()
                        renderer.close()
                        pfd.close()
                        viewModel.scanReceiptImage(bitmap)
                    }
                }
            } catch (e: Exception) {
                Log.e("PDF_RENDER", "Error rendering PDF", e)
            }
        }
    }

    if (currentUser == null) {
        AuthGateComponent(viewModel = viewModel)
    } else {
        Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showScanSelector = true },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.testTag("scan_floating_button")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.DocumentScanner,
                        contentDescription = "Scan Receipt"
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Scan Bill", fontWeight = FontWeight.SemiBold)
                }
            }
        },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Custom Clean Minimalism Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(100.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.ReceiptLong,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Text(
                        text = "Vault",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    val initialLabel = currentUser?.fullName?.split(" ")
                        ?.mapNotNull { it.firstOrNull()?.uppercaseChar() }
                        ?.take(2)
                        ?.joinToString("") ?: "JD"

                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(100.dp))
                            .clickable { showAccountDialog = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = initialLabel,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            // Stats Section
            ActiveWarrantiesDashboard(receipts = receipts)

            // Search Bar
            SearchBar(
                query = searchQuery,
                onQueryChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )

            // Filter lists
            val filteredReceipts = receipts.filter {
                it.merchantName.contains(searchQuery, ignoreCase = true) ||
                        it.itemsList.contains(searchQuery, ignoreCase = true)
            }

            if (filteredReceipts.isEmpty()) {
                EmptyReceiptsView(isSearchActive = searchQuery.isNotEmpty())
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "RECENT RECEIPTS",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }

                LazyColumn(
                    contentPadding = PaddingValues(bottom = 88.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filteredReceipts, key = { it.id }) { receipt ->
                        ReceiptCard(
                            receipt = receipt,
                            onClick = { selectedReceiptForDetail = receipt },
                            onDelete = { viewModel.deleteReceipt(receipt) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp)
                                .testTag("receipt_item_${receipt.id}")
                        )
                    }
                }
            }
        }
    }
    }

    // --- Configurations Overlay & Account Dialog ---
    if (showAccountDialog && currentUser != null) {
        AccountSettingsDialog(
            user = currentUser!!,
            notificationsEnabled = notificationsEnabled,
            remind30Days = remind30Days,
            remind7Days = remind7Days,
            remind1Day = remind1Day,
            viewModel = viewModel,
            onDismiss = { showAccountDialog = false }
        )
    }

    // --- Bottom Sheet/Dialog for Scan Picker Selection ---
    if (showScanSelector) {
        ScanSourceSelectorDialog(
            onDismiss = { showScanSelector = false },
            onPickFromGallery = {
                showScanSelector = false
                try {
                    photoPickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                } catch (e: Exception) {
                    Toast.makeText(context, "System photo picker is not available on this device", Toast.LENGTH_LONG).show()
                }
            },
            onTakePhoto = {
                showScanSelector = false
                try {
                    cameraLauncher.launch(null)
                } catch (e: Exception) {
                    Toast.makeText(context, "No camera application found on this device to take a custom photo", Toast.LENGTH_LONG).show()
                }
            },
            onPickPdfBill = {
                showScanSelector = false
                try {
                    pdfPickerLauncher.launch(arrayOf("application/pdf"))
                } catch (e: Exception) {
                    Toast.makeText(context, "No PDF document provider or file explorer found on this device", Toast.LENGTH_LONG).show()
                }
            },
            onScanMockReceipt = { merchantType ->
                showScanSelector = false
                val generatedBitmap = getMockReceiptBitmap(merchantType)
                viewModel.scanReceiptImage(generatedBitmap)
            }
        )
    }

    // --- Overlay Parsing/Scanning Screen ---
    AnimatedVisibility(
        visible = scanningState is ScanningUiState.Scanning,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        ScanningOverlayView()
    }

    // --- Dialog to edit/review extracted fields before saving ---
    val currentScanningState = scanningState
    if (currentScanningState is ScanningUiState.ParsedSuccess) {
        ReviewExtractedReceiptDialog(
            extracted = currentScanningState.extracted,
            sampleBitmap = currentScanningState.sampleBitmap,
            viewModel = viewModel,
            onDismiss = { viewModel.resetScanningState() },
            onSave = { details, duration, explicitExpiry ->
                viewModel.saveReceipt(
                    merchantName = details.merchantName ?: "",
                    purchaseDate = details.purchaseDate ?: "",
                    totalAmount = details.totalAmount ?: 0.0,
                    currency = details.currency ?: "USD",
                    itemsList = details.itemsList ?: "",
                    hasWarranty = details.hasWarranty ?: true,
                    warrantyDurationMonths = duration,
                    supportContact = details.supportContact ?: "",
                    explicitExpiryDate = explicitExpiry
                )
            }
        )
    }

    // --- Scanning/parsing errors ---
    if (currentScanningState is ScanningUiState.Error) {
        AlertDialog(
            onDismissRequest = { viewModel.resetScanningState() },
            confirmButton = {
                TextButton(onClick = { viewModel.resetScanningState() }) {
                    Text("OK")
                }
            },
            title = { Text("OCR Parsing Error") },
            text = { Text(currentScanningState.message) },
            icon = { Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error) }
        )
    }

    // --- Detail View Dialog ---
    selectedReceiptForDetail?.let { receipt ->
        ReceiptDetailDialog(
            receipt = receipt,
            onDismiss = { selectedReceiptForDetail = null }
        )
    }
}

// ==================== DASHBOARD / STATS SECTION ====================

@Composable
fun ActiveWarrantiesDashboard(receipts: List<Receipt>) {
    val totalReceipts = receipts.size
    val activeWarranties = receipts.count { it.hasWarranty && !it.isExpired() }
    val expiringSoon = receipts.count {
        it.hasWarranty && !it.isExpired() && it.getDaysRemaining() in 0..30
    }
    val totalProtectedValue = receipts.sumOf { it.totalAmount }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            // Header: Total Protected Value + Premium Tag
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column {
                    Text(
                        text = "Total Protected Value",
                        fontWeight = FontWeight.Medium,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "$${String.format("%,.2f", totalProtectedValue)}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 30.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }

                Box(
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(100.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "PREMIUM",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Stats Sub-cards Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Active Card
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(CompactActiveBg, RoundedCornerShape(16.dp))
                        .padding(12.dp)
                ) {
                    Column {
                        Text(
                            text = "ACTIVE",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = CompactActiveText
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = activeWarranties.toString(),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = CompactActiveText
                        )
                    }
                }

                // Expiring Card
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(ExpiringBackground, RoundedCornerShape(16.dp))
                        .padding(12.dp)
                ) {
                    Column {
                        Text(
                            text = "EXPIRING",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = ExpiringText
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = expiringSoon.toString(),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = ExpiringText
                        )
                    }
                }
            }
        }
    }
}

// ==================== RECEIPT COMPONENT CARDS ====================

@Composable
fun ReceiptCard(
    receipt: Receipt,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = RoundedCornerShape(20.dp),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon Indicator inside a rounded-xl container
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (receipt.hasWarranty) {
                        if (receipt.isExpired()) Icons.Default.Gavel
                        else Icons.Default.Shield
                    } else {
                        Icons.Default.Receipt
                    },
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Text Content split into top and bottom rows
            Column(modifier = Modifier.weight(1f)) {
                // Top Row: Merchant Name + Price
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = receipt.merchantName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Text(
                        text = "${receipt.currency} ${String.format("%.0f", receipt.totalAmount)}",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Bottom Row: Items description + Status Badge
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = receipt.itemsList,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.secondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Status Badge
                        if (receipt.hasWarranty) {
                            val days = receipt.getDaysRemaining()
                            if (receipt.isExpired()) {
                                StatusBadge("No Warranty", MaterialTheme.colorScheme.onSecondaryContainer, MaterialTheme.colorScheme.secondaryContainer)
                            } else if (days <= 30) {
                                StatusBadge("Exp. ${days}d", ExpiringBadgeText, ExpiringBadgeBg)
                            } else {
                                val yrs = if (days >= 365) "${days / 365}Y" else "${days}d"
                                StatusBadge("$yrs Warranty", ActiveBadgeText, ActiveBadgeBg)
                            }
                        } else {
                            StatusBadge("No Warranty", MaterialTheme.colorScheme.onSecondaryContainer, MaterialTheme.colorScheme.secondaryContainer)
                        }

                        // Subtle Trash Icon
                        IconButton(
                            onClick = { showDeleteConfirm = true },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Delete,
                                contentDescription = "Delete Receipt",
                                tint = Color.Gray.copy(alpha = 0.5f),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        onDelete()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            },
            title = { Text("Delete Receipt") },
            text = { Text("Are you sure you want to permanently delete this receipt and its warranty reminders?") }
        )
    }
}

@Composable
fun StatusBadge(text: String, textColor: Color, backgroundColor: Color) {
    Box(
        modifier = Modifier
            .background(backgroundColor, RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text = text,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = textColor
        )
    }
}

// ==================== SEARCH & EMPTY VIEWS ====================

@Composable
fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = { Text("Search Items") },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Default.Clear, contentDescription = "Clear search")
                }
            }
        },
        maxLines = 1,
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MinimalTextFieldContainer,
            unfocusedContainerColor = MinimalTextFieldContainer,
            focusedTextColor = MinimalTextFieldText,
            unfocusedTextColor = MinimalTextFieldText,
            focusedPlaceholderColor = Color.Gray,
            unfocusedPlaceholderColor = Color.Gray,
            focusedBorderColor = Color.Transparent,
            unfocusedBorderColor = Color.Transparent
        ),
        modifier = modifier
    )
}

@Composable
fun EmptyReceiptsView(isSearchActive: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = if (isSearchActive) Icons.Default.SearchOff else Icons.Outlined.FolderOpen,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
            modifier = Modifier.size(72.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = if (isSearchActive) "No Search Results" else "No Receipts Saved",
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = if (isSearchActive) {
                "Try searching for another keyword or merchant."
            } else {
                "Scan your first grocery bill, electronic invoice, or receipt using Gemini OCR to extract warranties automatically!"
            },
            color = Color.Gray,
            fontSize = 13.sp,
            modifier = Modifier.padding(horizontal = 32.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

// ==================== SCAN SELECTOR DRAWER/DIALOG ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanSourceSelectorDialog(
    onDismiss: () -> Unit,
    onPickFromGallery: () -> Unit,
    onTakePhoto: () -> Unit,
    onPickPdfBill: () -> Unit,
    onScanMockReceipt: (Int) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        title = {
            Text(
                "Scan or Import Receipt",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.primary
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Choose an option to parse your receipt with Gemini's high-accuracy AI extractor.",
                    fontSize = 12.sp,
                    color = Color.Gray,
                    lineHeight = 16.sp
                )

                // GALLERY PICKER
                Button(
                    onClick = onPickFromGallery,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Pick From Gallery", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }

                // CAMERA PHOTO
                Button(
                    onClick = onTakePhoto,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Take Custom Photo", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }

                // PDF BILL
                Button(
                    onClick = onPickPdfBill,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Select PDF Bill", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                Text(
                    "Simulate Retail Scan (Testing)",
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.primary
                )

                OutlinedButton(
                    onClick = { onScanMockReceipt(1) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 40.dp),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Tv, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Best Buy (Sony OLED TV)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                OutlinedButton(
                    onClick = { onScanMockReceipt(2) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 40.dp),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Kitchen, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Home Depot (LG Fridge)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                OutlinedButton(
                    onClick = { onScanMockReceipt(3) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 40.dp),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Tablet, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Apple Store (iPad Pro)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    )
}

// ==================== SCANNING ANIMATION PAGE ====================

@Composable
fun ScanningOverlayView() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.82f))
            .clickable(enabled = false) {}, // Intercept events
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            // Animated Scanning graphic
            Box(
                modifier = Modifier
                    .size(120.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.fillMaxSize(),
                    strokeWidth = 6.dp,
                    color = MaterialTheme.colorScheme.primary
                )
                Icon(
                    imageVector = Icons.Default.DocumentScanner,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(48.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                "Scanning Bill with Gemini...",
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                "Uploading receipt pixels, invoking Gemini 3.5 Flash server-side parser to translate unstructured layout into highly secure JSON schema payload...",
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.7f),
                fontFamily = FontFamily.Monospace,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

// ==================== REVIEW EXTRACTED DETAILS DIALOG ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewExtractedReceiptDialog(
    extracted: ExtractedReceipt,
    sampleBitmap: Bitmap?,
    viewModel: ReceiptViewModel,
    onDismiss: () -> Unit,
    onSave: (ExtractedReceipt, Int, String) -> Unit
) {
    var merchant by remember { mutableStateOf(extracted.merchantName ?: "") }
    var purchaseDate by remember { mutableStateOf(extracted.purchaseDate ?: "") }
    var totalAmountStr by remember { mutableStateOf(extracted.totalAmount?.toString() ?: "0.0") }
    var currency by remember { mutableStateOf(extracted.currency ?: "USD") }
    var itemsList by remember { mutableStateOf(extracted.itemsList ?: "") }
    var hasWarranty by remember { mutableStateOf(extracted.hasWarranty ?: true) }
    var warrantyDurationMonthsStr by remember { mutableStateOf("12") }
    var supportContact by remember { mutableStateOf(extracted.supportContact ?: "") }

    var productPageLink by remember { mutableStateOf("") }
    var explicitExpiryDate by remember { mutableStateOf("") }
    var isExtractingFromUrl by remember { mutableStateOf(false) }
    var extractionErrorMsg by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
        ) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text("Verify Scanned Details", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
                        navigationIcon = {
                            IconButton(onClick = onDismiss) {
                                Icon(Icons.Default.Close, contentDescription = "Close")
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
                    )
                },
                bottomBar = {
                    Surface(
                        tonalElevation = 8.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedButton(
                                onClick = onDismiss,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Discard")
                            }

                            Button(
                                onClick = {
                                    val finalAmount = totalAmountStr.toDoubleOrNull() ?: 0.0
                                    val duration = warrantyDurationMonthsStr.toIntOrNull() ?: 0
                                    onSave(
                                        ExtractedReceipt(
                                            merchantName = merchant,
                                            purchaseDate = purchaseDate,
                                            totalAmount = finalAmount,
                                            currency = currency,
                                            itemsList = itemsList,
                                            hasWarranty = hasWarranty,
                                            warrantyExpiryDate = "", // Calculated in ViewModel
                                            supportContact = supportContact
                                        ),
                                        duration,
                                        explicitExpiryDate
                                    )
                                },
                                modifier = Modifier.weight(1.3f),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Icon(Icons.Default.Save, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Save & Alert", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            ) { innerPadding ->
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    item {
                        Text(
                            "The Gemini 3.5 Flash OCR successfully processed the bill image. Verify extracted fields below before committing to database:",
                            fontSize = 12.sp,
                            color = Color.Gray,
                            lineHeight = 16.sp
                        )
                    }

                    // Thumbnail preview if available
                    sampleBitmap?.let { bitmap ->
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(130.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color.LightGray),
                                contentAlignment = Alignment.Center
                            ) {
                                androidx.compose.foundation.Image(
                                    bitmap = bitmap.asImageBitmap(),
                                    contentDescription = "Receipt Scan Preview",
                                    modifier = Modifier.fillMaxSize()
                                )
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(topStart = 8.dp))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text("Captured", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    item {
                        OutlinedTextField(
                            value = merchant,
                            onValueChange = { merchant = it },
                            label = { Text("Merchant Name") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }

                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = purchaseDate,
                                onValueChange = { purchaseDate = it },
                                label = { Text("Purchase Date") },
                                modifier = Modifier.weight(1.1f),
                                placeholder = { Text("YYYY-MM-DD") },
                                shape = RoundedCornerShape(12.dp)
                            )

                            OutlinedTextField(
                                value = currency,
                                onValueChange = { currency = it },
                                label = { Text("Currency") },
                                modifier = Modifier.weight(0.9f),
                                placeholder = { Text("USD") },
                                shape = RoundedCornerShape(12.dp)
                            )
                        }
                    }

                    item {
                        OutlinedTextField(
                            value = totalAmountStr,
                            onValueChange = { totalAmountStr = it },
                            label = { Text("Total Bill Amount") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }

                    item {
                        OutlinedTextField(
                            value = itemsList,
                            onValueChange = { itemsList = it },
                            label = { Text("Purchased Items") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }

                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f),
                                    RoundedCornerShape(12.dp)
                                )
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Save Warranty Guard Reminder",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Text(
                                    "Schedule Local Alerts before expiration",
                                    fontSize = 11.sp,
                                    color = Color.Gray
                                )
                            }
                            Switch(
                                checked = hasWarranty,
                                onCheckedChange = { hasWarranty = it }
                            )
                        }
                    }

                    if (hasWarranty) {
                        item {
                            OutlinedTextField(
                                value = warrantyDurationMonthsStr,
                                onValueChange = { 
                                    warrantyDurationMonthsStr = it 
                                    if (it.isNotBlank()) explicitExpiryDate = ""
                                },
                                label = { Text("Warranty Duration (Months)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            )
                        }

                        // Specific Warranty Expiry Date manual input field
                        item {
                            OutlinedTextField(
                                value = explicitExpiryDate,
                                onValueChange = { 
                                    explicitExpiryDate = it
                                    if (it.isNotBlank()) warrantyDurationMonthsStr = ""
                                },
                                label = { Text("Specific Expiry Date (YYYY-MM-DD)") },
                                placeholder = { Text("e.g. 2026-11-20") },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            )
                        }

                        // Product Page Link section for dynamic AI extraction
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f))
                            ) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        "Extract from Product Page URL",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        "Paste product page link and click below. Gemini will scan the HTML content dynamically for warranty period info.",
                                        fontSize = 11.sp,
                                        lineHeight = 15.sp,
                                        color = Color.Gray
                                    )
                                    
                                    OutlinedTextField(
                                        value = productPageLink,
                                        onValueChange = { productPageLink = it },
                                        placeholder = { Text("https://www.ikea.com/chair-xyz") },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(8.dp),
                                        colors = TextFieldDefaults.colors(
                                            focusedContainerColor = Color.Transparent,
                                            unfocusedContainerColor = Color.Transparent
                                        ),
                                        singleLine = true
                                    )

                                    if (isExtractingFromUrl) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(top = 4.dp)
                                        ) {
                                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Gemini scanning webpage details...", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                                        }
                                    }

                                    extractionErrorMsg?.let { error ->
                                        Text(error, color = MaterialTheme.colorScheme.primary, fontSize = 11.sp)
                                    }

                                    Button(
                                        onClick = {
                                            if (productPageLink.isNotBlank()) {
                                                isExtractingFromUrl = true
                                                extractionErrorMsg = null
                                                scope.launch {
                                                    val months = viewModel.extractWarrantyDurationFromUrl(productPageLink)
                                                    isExtractingFromUrl = false
                                                    if (months != null) {
                                                        warrantyDurationMonthsStr = months.toString()
                                                        explicitExpiryDate = ""
                                                        extractionErrorMsg = "Success! Extracted $months months warranty!"
                                                    } else {
                                                        extractionErrorMsg = "Could not locate warranty duration automatically. Please type it manually."
                                                    }
                                                }
                                            } else {
                                                extractionErrorMsg = "Please paste a product URL first."
                                            }
                                        },
                                        enabled = !isExtractingFromUrl,
                                        modifier = Modifier.align(Alignment.End),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("AI Extract Link Info", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }

                    item {
                        OutlinedTextField(
                            value = supportContact,
                            onValueChange = { supportContact = it },
                            label = { Text("Customer Support Helpline / Link") },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("e.g., support@bestbuy.com") },
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                }
            }
        }
    }
}

// ==================== RECEIPT DETAIL DIALOG VIEW ====================

@Composable
fun ReceiptDetailDialog(
    receipt: Receipt,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                // Header with status indicator
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Receipt Receipt",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontFamily = FontFamily.Monospace
                    )

                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = receipt.merchantName,
                    fontWeight = FontWeight.Black,
                    fontSize = 26.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Text(
                    text = "Billed Date: ${receipt.purchaseDate}",
                    fontSize = 13.sp,
                    color = Color.Gray
                )

                Spacer(modifier = Modifier.height(16.dp))

                HorizontalDivider()

                Spacer(modifier = Modifier.height(16.dp))

                // Amount
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Total Amount Paid", fontSize = 14.sp, color = Color.Gray)
                    Text(
                        text = "${receipt.currency} ${String.format("%.2f", receipt.totalAmount)}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Items list
                Text(
                    text = "ITEMS LISTED",
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = Color.LightGray
                )
                Spacer(modifier = Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    Text(
                        text = receipt.itemsList,
                        fontSize = 13.sp,
                        fontFamily = FontFamily.SansSerif,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Warranty Expiry
                if (receipt.hasWarranty) {
                    Text(
                        text = "WARRANTY DETAILS",
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = Color.LightGray
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                color = if (receipt.isExpired()) {
                                    MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)
                                } else {
                                    MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.2f)
                                },
                                shape = RoundedCornerShape(12.dp)
                            )
                            .padding(12.dp)
                    ) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Ends On Date", fontSize = 13.sp, color = Color.DarkGray)
                                Text(
                                    text = receipt.warrantyExpiryDate,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = if (receipt.isExpired()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Status Indicator", fontSize = 12.sp, color = Color.Gray)
                                val days = receipt.getDaysRemaining()
                                if (receipt.isExpired()) {
                                    Text("Warranty Expired", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                } else if (days <= 30) {
                                    Text("Expires in $days days", color = ExpiringText, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                } else {
                                    Text("$days Days Remaining", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }

                if (receipt.supportContact.isNotBlank()) {
                    Text(
                        text = "SUPPORT HELPLINE / LINK",
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = Color.LightGray
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Campaign,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = receipt.supportContact,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Close Panel")
                }
            }
        }
    }
}

// ==================== MOCK RECEIPT GRAPHICS GENERATION ====================

fun getMockReceiptBitmap(type: Int): Bitmap {
    val title = when (type) {
        1 -> "Best Buy Store"
        2 -> "Home Depot Center"
        else -> "Apple Retail Store"
    }
    val date = when (type) {
        1 -> "2026-05-15"
        2 -> "2026-04-20"
        else -> "2026-05-10"
    }
    val item = when (type) {
        1 -> "Sony Bravia 55\" OLED Smart TV"
        2 -> "LG Double Door Counter Depth Refrigerator"
        else -> "iPad Pro 11\" Liquid Retina 256GB"
    }
    val price = when (type) {
        1 -> "1299.99"
        2 -> "1899.99"
        else -> "899.00"
    }
    val warranty = when (type) {
        1 -> "24 Month Limited Warranty policy"
        2 -> "1 Year Hardware Guard Service Coverage"
        else -> "AppleOne Direct Support - standard 12 months coverage"
    }

    // Programmatically render the text receipt on custom white bitmap
    val bitmap = Bitmap.createBitmap(550, 750, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    val paint = android.graphics.Paint()

    paint.color = android.graphics.Color.WHITE
    canvas.drawRect(0f, 0f, 550f, 750f, paint)

    val textPaint = android.graphics.Paint().apply {
        color = android.graphics.Color.BLACK
        textSize = 24f
        isAntiAlias = true
    }

    var y = 70f

    // Header Address Details
    textPaint.textSize = 34f
    textPaint.isFakeBoldText = true
    canvas.drawText(title.uppercase(), 40f, y, textPaint)
    y += 45f

    textPaint.textSize = 19f
    textPaint.isFakeBoldText = false
    textPaint.color = android.graphics.Color.DKGRAY
    canvas.drawText("100 Tech Galleria Mall, Suite #40-B", 40f, y, textPaint)
    y += 35f
    canvas.drawText("Helpline support number: 1-800-442-VAULT", 40f, y, textPaint)
    y += 35f
    canvas.drawText("Billed on Date: $date", 40f, y, textPaint)
    y += 45f

    val linePaint = android.graphics.Paint().apply {
        color = android.graphics.Color.GRAY
        strokeWidth = 3f
    }
    canvas.drawLine(40f, y, 510f, y, linePaint)
    y += 50f

    // Items Section
    textPaint.textSize = 22f
    textPaint.isFakeBoldText = true
    textPaint.color = android.graphics.Color.BLACK
    canvas.drawText("ITEMS LIST:", 40f, y, textPaint)
    y += 40f

    textPaint.isFakeBoldText = false
    canvas.drawText("1x $item", 40f, y, textPaint)
    canvas.drawText("$$price", 400f, y, textPaint)
    y += 55f

    canvas.drawLine(40f, y, 510f, y, linePaint)
    y += 50f

    // Total Section
    textPaint.textSize = 28f
    textPaint.isFakeBoldText = true
    canvas.drawText("TOTAL", 40f, y, textPaint)
    canvas.drawText("USD $$price", 340f, y, textPaint)
    y += 60f

    // Warranty / Customer notes details
    textPaint.textSize = 18f
    textPaint.isFakeBoldText = false
    textPaint.color = android.graphics.Color.DKGRAY
    canvas.drawText("SPECIAL CUSTOMER NOTICE:", 40f, y, textPaint)
    y += 35f
    canvas.drawText(warranty, 40f, y, textPaint)
    y += 35f
    canvas.drawText("Register support disputes at help@vault.com", 40f, y, textPaint)
    y += 50f

    // Barcode Simulation
    val barcodePaint = android.graphics.Paint().apply {
        color = android.graphics.Color.BLACK
        strokeWidth = 4f
    }
    var barX = 120f
    for (i in 0..45) {
        val spacing = (4..12).random().toFloat()
        canvas.drawLine(barX, y, barX, y + 60f, barcodePaint)
        barX += spacing
    }

    return bitmap
}

@Composable
fun AuthGateComponent(viewModel: ReceiptViewModel) {
    var isSignUp by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var fullName by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(24.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 420.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header Logo
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(100.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.ReceiptLong,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = if (isSignUp) "Create Account" else "Welcome Back",
                    fontWeight = FontWeight.Bold,
                    fontSize = 24.sp,
                    color = MaterialTheme.colorScheme.onBackground
                )

                Text(
                    text = if (isSignUp) "Sign up to securely back up all receipts" else "Log in to manage your warranties securely",
                    fontSize = 13.sp,
                    color = Color.Gray,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp, bottom = 20.dp)
                )

                if (errorMessage != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(SoftAlertBg, RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = errorMessage ?: "",
                            color = SoftAlertText,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                if (isSignUp) {
                    OutlinedTextField(
                        value = fullName,
                        onValueChange = { fullName = it },
                        label = { Text("Full Name") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("auth_name_input"),
                        shape = RoundedCornerShape(12.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email Address") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("auth_email_input"),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("auth_password_input"),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = {
                        errorMessage = null
                        isLoading = true
                        if (isSignUp) {
                            viewModel.signUp(email, password, fullName) { success, msg ->
                                isLoading = false
                                if (!success) errorMessage = msg
                            }
                        } else {
                            viewModel.logIn(email, password) { success, msg ->
                                isLoading = false
                                if (!success) errorMessage = msg
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("auth_submit_button"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    } else {
                        Text(if (isSignUp) "Register" else "Log In", fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = if (isSignUp) "Already have an account? Log In" else "New to ReceiptVault? Sign Up",
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clickable {
                            isSignUp = !isSignUp
                            errorMessage = null
                        }
                        .padding(8.dp)
                )
            }
        }
    }
}

@Composable
fun AccountSettingsDialog(
    user: User,
    notificationsEnabled: Boolean,
    remind30Days: Boolean,
    remind7Days: Boolean,
    remind1Day: Boolean,
    viewModel: ReceiptViewModel,
    onDismiss: () -> Unit
) {
    val customApiKey by viewModel.customGeminiApiKey.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val profilePicKey = "display_picture_uri_${user.email}"
    val sharedPrefs = remember(user.email) { context.getSharedPreferences("vault_auth_prefs", Context.MODE_PRIVATE) }
    var profilePicUri by remember(user.email) { mutableStateOf(sharedPrefs.getString(profilePicKey, null)) }
    val pickImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            sharedPrefs.edit().putString(profilePicKey, uri.toString()).apply()
            profilePicUri = uri.toString()
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(24.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                // Header
                Text(
                    text = "Account & Settings",
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = MaterialTheme.colorScheme.onBackground
                )

                Spacer(modifier = Modifier.height(16.dp))

                // User Info Card
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                        .padding(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(54.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.secondary)
                                .clickable { pickImageLauncher.launch("image/*") },
                            contentAlignment = Alignment.Center
                        ) {
                            if (profilePicUri != null) {
                                androidx.compose.foundation.Image(
                                    painter = rememberAsyncImagePainter(profilePicUri),
                                    contentDescription = "Profile Picture",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                val initials = user.fullName.split(" ")
                                    .mapNotNull { it.firstOrNull()?.uppercaseChar() }
                                    .take(2)
                                    .joinToString("")
                                Text(
                                    text = initials.ifBlank { "U" },
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = user.fullName,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.onBackground,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = user.email,
                                fontSize = 12.sp,
                                color = Color.Gray,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Tap icon to change",
                                fontSize = 10.sp,
                                color = PlainSecondaryAccent,
                                modifier = Modifier.clickable { pickImageLauncher.launch("image/*") }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Notification Settings Header
                Text(
                    text = "EXPIRATION ALERTS",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    color = Color.Gray
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Toggle 1: Global notifications
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Enable Guarantee Reminders",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Switch(
                        checked = notificationsEnabled,
                        onCheckedChange = {
                            viewModel.updateNotificationPrefs(it, remind30Days, remind7Days, remind1Day)
                        },
                        modifier = Modifier
                            .scale(0.85f)
                            .testTag("settings_global_notifications")
                    )
                }

                // If global notifications enabled, show offsets thresholds
                if (notificationsEnabled) {
                    Spacer(modifier = Modifier.height(4.dp))

                    // 30 days
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Alert 30 Days Before Expiration",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Checkbox(
                            checked = remind30Days,
                            onCheckedChange = {
                                viewModel.updateNotificationPrefs(notificationsEnabled, it, remind7Days, remind1Day)
                            },
                            modifier = Modifier
                                .scale(0.85f)
                                .testTag("settings_alert_30")
                        )
                    }

                    // 7 days
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Alert 7 Days Before Expiration",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Checkbox(
                            checked = remind7Days,
                            onCheckedChange = {
                                viewModel.updateNotificationPrefs(notificationsEnabled, remind30Days, it, remind1Day)
                            },
                            modifier = Modifier
                                .scale(0.85f)
                                .testTag("settings_alert_7")
                        )
                    }

                    // 1 day
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Alert 1 Day Before Expiration",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Checkbox(
                            checked = remind1Day,
                            onCheckedChange = {
                                viewModel.updateNotificationPrefs(notificationsEnabled, remind30Days, remind7Days, it)
                            },
                            modifier = Modifier
                                .scale(0.85f)
                                .testTag("settings_alert_1")
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Gemini API Key Section - Inline single-row design
                var apiKeyInput by remember { mutableStateOf(customApiKey) }
                var showPromoKey by remember { mutableStateOf(false) }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "API Key",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )

                    OutlinedTextField(
                        value = apiKeyInput,
                        onValueChange = {
                            apiKeyInput = it
                            viewModel.updateCustomGeminiApiKey(it)
                        },
                        placeholder = { Text("API Key") },
                        singleLine = true,
                        visualTransformation = if (showPromoKey) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showPromoKey = !showPromoKey }) {
                                Icon(
                                    imageVector = if (showPromoKey) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = if (showPromoKey) "Toggle Visibility" else "Toggle Visibility",
                                    tint = MinimalTextFieldText,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        },
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MinimalTextFieldContainer,
                            unfocusedContainerColor = MinimalTextFieldContainer,
                            focusedTextColor = MinimalTextFieldText,
                            unfocusedTextColor = MinimalTextFieldText,
                            focusedPlaceholderColor = Color.Gray,
                            unfocusedPlaceholderColor = Color.Gray,
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("settings_custom_api_key_field")
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = {
                            viewModel.logOut()
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = SoftAlertBg,
                            contentColor = SoftAlertText
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("settings_logout_btn"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Log Out", maxLines = 1, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }

                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("settings_close_btn"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Done", maxLines = 1, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}
