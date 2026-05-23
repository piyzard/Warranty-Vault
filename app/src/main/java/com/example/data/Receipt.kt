package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Entity(tableName = "receipts")
data class Receipt(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val merchantName: String,
    val purchaseDate: String, // YYYY-MM-DD
    val totalAmount: Double,
    val currency: String,
    val itemsList: String, // Comma-separated or format list
    val hasWarranty: Boolean,
    val warrantyExpiryDate: String, // YYYY-MM-DD
    val supportContact: String,
    val userId: String = "default_user"
) {
    // Helper to calculate expiry countdown in days
    fun getDaysRemaining(): Long {
        if (!hasWarranty || warrantyExpiryDate.isBlank()) return -1
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val expiry = sdf.parse(warrantyExpiryDate) ?: return -1
            val today = sdf.parse(sdf.format(Date())) ?: return -1
            val diff = expiry.time - today.time
            diff / (1000 * 60 * 60 * 24)
        } catch (e: Exception) {
            -1
        }
    }

    // Is warranty expired
    fun isExpired(): Boolean {
        val days = getDaysRemaining()
        return hasWarranty && days < 0
    }
}
