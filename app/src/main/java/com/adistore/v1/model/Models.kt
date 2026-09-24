package com.adistore.v1.model

import kotlinx.serialization.Serializable

@Serializable
data class Profile(
    val id: String,
    val full_name: String? = null,
    val phone: String? = null,
    val role: String = "customer"
)

@Serializable
data class Product(
    val id: String,
    val title: String,
    val description: String? = null,
    val price: Double,
    val image_url: String? = null,
    val status: String = "active"
)

@Serializable
data class Order(
    val id: String,
    val user_id: String,
    val product_id: String? = null,
    val product_title: String,
    val amount: Double,
    val status: String,
    val created_at: String? = null
)

@Serializable
data class Payment(
    val id: String,
    val order_id: String,
    val amount: Double,
    val payment_method: String = "upi",
    val utr: String? = null,
    val status: String = "pending",
    val submitted_at: String? = null,
    val admin_note: String? = null
)

@Serializable
data class PaymentProof(
    val id: String,
    val order_id: String,
    val payment_id: String? = null,
    val storage_path: String,
    val uploaded_by: String
)

@Serializable
data class DeliveryResult(val delivery_data: String? = null)
