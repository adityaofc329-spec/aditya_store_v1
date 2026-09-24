package com.adistore.v1.data

import com.adistore.v1.model.*
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.providers.Email
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.rpc
import io.github.jan.supabase.storage.Storage
import io.ktor.client.engine.android.Android
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

object SupabaseRepository {
    val client: SupabaseClient by lazy {
        createSupabaseClient(
            supabaseUrl = SupabaseConfig.URL,
            supabaseKey = SupabaseConfig.PUBLISHABLE_KEY
        ) {
            install(Auth)
            install(Postgrest)
            install(Storage)
            httpEngine = Android.create()
        }
    }

    suspend fun signIn(email: String, password: String) {
        client.auth.signInWith(Email) {
            this.email = email
            this.password = password
        }
    }

    suspend fun signUp(email: String, password: String, name: String) {
        client.auth.signUpWith(Email) {
            this.email = email
            this.password = password
            data = buildJsonObject { put("full_name", name) }
        }
    }

    suspend fun signOut() = client.auth.signOut()
    fun isSignedIn(): Boolean = client.auth.currentSessionOrNull() != null
    fun currentUserId(): String? = client.auth.currentSessionOrNull()?.user?.id

    suspend fun products(): List<Product> =
        client.from("products").select {
            filter { eq("status", "active") }
        }.decodeList()

    suspend fun myOrders(): List<Order> =
        client.from("orders").select {
            order("created_at", io.github.jan.supabase.postgrest.query.Order.DESCENDING)
        }.decodeList()

    suspend fun myPayments(): List<Payment> =
        client.from("payments").select().decodeList()

    suspend fun profile(): Profile? {
        val uid = currentUserId() ?: return null
        return client.from("profiles").select {
            filter { eq("id", uid) }
        }.decodeList<Profile>().firstOrNull()
    }

    suspend fun createOrder(product: Product): Order {
        val uid = currentUserId() ?: error("Please login first")
        return client.from("orders").insert(
            buildJsonObject {
                put("user_id", uid)
                put("product_id", product.id)
                put("product_title", product.title)
                put("amount", product.price)
                put("status", "pending_payment")
            }
        ) { select() }.decodeSingle()
    }

    suspend fun createPayment(order: Order): Payment {
        return client.from("payments").insert(
            buildJsonObject {
                put("order_id", order.id)
                put("amount", order.amount)
                put("payment_method", "upi")
                put("status", "pending")
            }
        ) { select() }.decodeSingle()
    }

    suspend fun submitPaymentForReview(orderId: String, utr: String) =
        client.postgrest.rpc("submit_payment_for_review", mapOf("p_order_id" to orderId, "p_utr" to utr))

    suspend fun uploadPaymentProof(orderId: String, paymentId: String?, bytes: ByteArray, extension: String): String {
        val uid = currentUserId() ?: error("Please login first")
        val path = "$uid/$orderId/payment_${System.currentTimeMillis()}.$extension"
        client.storage.from(SupabaseConfig.PAYMENT_BUCKET).upload(path, bytes) { upsert = false }
        client.from("payment_proofs").insert(
            buildJsonObject {
                put("order_id", orderId)
                if (paymentId != null) put("payment_id", paymentId)
                put("storage_path", path)
                put("uploaded_by", uid)
            }
        )
        return path
    }

    suspend fun approveOrder(orderId: String, note: String? = null) =
        client.postgrest.rpc("approve_order", mapOf("p_order_id" to orderId, "p_admin_note" to note))

    suspend fun rejectOrder(orderId: String, reason: String) =
        client.postgrest.rpc("reject_order", mapOf("p_order_id" to orderId, "p_reason" to reason))

    suspend fun approvedDelivery(orderId: String): String? =
        client.postgrest.rpc("get_approved_delivery", mapOf("p_order_id" to orderId)).decodeSingle<DeliveryResult>().delivery_data

    suspend fun reviewOrders(): List<Order> =
        client.from("orders").select { filter { eq("status", "under_review") } }.decodeList()

    suspend fun paymentForOrder(orderId: String): Payment? =
        client.from("payments").select { filter { eq("order_id", orderId) } }.decodeList<Payment>().firstOrNull()

    suspend fun proofsForOrder(orderId: String): List<PaymentProof> =
        client.from("payment_proofs").select { filter { eq("order_id", orderId) } }.decodeList()

    suspend fun signedProofUrl(path: String): String =
        client.storage.from(SupabaseConfig.PAYMENT_BUCKET).createSignedUrl(path, 600)

    suspend fun adminProducts(): List<Product> = client.from("products").select().decodeList()

    suspend fun addProduct(title: String, description: String, price: Double, imageUrl: String?, deliveryData: String?) {
        client.from("products").insert(buildJsonObject {
            put("title", title)
            put("description", description)
            put("price", price)
            if (!imageUrl.isNullOrBlank()) put("image_url", imageUrl)
            if (!deliveryData.isNullOrBlank()) put("delivery_data", deliveryData)
            put("status", "active")
        })
    }

    suspend fun setProductStatus(id: String, status: String) {
        client.from("products").update(buildJsonObject { put("status", status) }) {
            filter { eq("id", id) }
        }
    }
}
