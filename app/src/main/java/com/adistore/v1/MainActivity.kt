package com.adistore.v1

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.adistore.v1.data.SupabaseConfig
import com.adistore.v1.data.SupabaseRepository
import com.adistore.v1.model.Order
import com.adistore.v1.model.Product
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {
    private lateinit var root: LinearLayout
    private lateinit var content: LinearLayout
    private var selectedProduct: Product? = null
    private var selectedOrder: Order? = null
    private var selectedProofUri: Uri? = null
    private var currentProofUrl: String? = null
    private val pickProof = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        selectedProofUri = uri
        if (uri != null) Toast.makeText(this, "Screenshot selected", Toast.LENGTH_SHORT).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 24, 24, 24) }
        setContentView(root)
        if (SupabaseRepository.isSignedIn()) showHome() else showAuth()
    }

    private fun text(s: String, size: Float = 15f) = TextView(this).apply { text = s; textSize = size; setPadding(0, 8, 0, 8) }
    private fun button(s: String, action: () -> Unit) = Button(this).apply { text = s; setOnClickListener { action() } }
    private fun field(hint: String, password: Boolean = false) = EditText(this).apply { this.hint = hint; setPadding(16, 12, 16, 12); if (password) inputType = 0x81 }
    private fun base(title: String) {
        root.removeAllViews()
        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val titleView = text(title, 24f).apply { layoutParams = LinearLayout.LayoutParams(0, -2, 1f) }
        header.addView(titleView)
        if (SupabaseRepository.isSignedIn()) header.addView(button("Logout") { lifecycleScope.launch { runCatching { SupabaseRepository.signOut() }; showAuth() } })
        root.addView(header)
        content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, 10, 0, 0) }
        val scroll = ScrollView(this).apply { addView(content) }
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
    }

    private fun showAuth() {
        base("ADI_STORE_V1")
        val email = field("Email")
        val pass = field("Password", true)
        val name = field("Full name (signup only)")
        val status = text("Login to continue")
        content.addView(text("Customer / Admin login", 18f)); content.addView(email); content.addView(pass); content.addView(name)
        content.addView(button("Login") {
            lifecycleScope.launch { try { SupabaseRepository.signIn(email.text.toString().trim(), pass.text.toString()); showHome() } catch(e: Exception) { status.text = "Login failed: ${e.message}" } }
        })
        content.addView(button("Create account") {
            lifecycleScope.launch { try { SupabaseRepository.signUp(email.text.toString().trim(), pass.text.toString(), name.text.toString().trim()); status.text = "Signup successful. If email confirmation is enabled, verify email then login." } catch(e: Exception) { status.text = "Signup failed: ${e.message}" } }
        })
        content.addView(status)
    }

    private fun showHome() {
        base("ADI_STORE_V1")
        content.addView(text("Digital marketplace", 18f))
        content.addView(button("Browse Store") { showStore() })
        content.addView(button("My Orders") { showOrders() })
        content.addView(button("Admin Panel") { showAdmin() })
        content.addView(button("Account") { showAccount() })
        lifecycleScope.launch { val p = runCatching { SupabaseRepository.profile() }.getOrNull(); if (p != null) content.addView(text("Signed in as ${p.full_name ?: "Customer"} • ${p.role}")) }
    }

    private fun showStore() {
        base("Store")
        content.addView(text("Active products", 20f))
        lifecycleScope.launch {
            try {
                val products = SupabaseRepository.products()
                if (products.isEmpty()) content.addView(text("No products available yet."))
                products.forEach { product ->
                    val card = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL; setPadding(16, 16, 16, 16) }
                    card.addView(text(product.title, 19f)); card.addView(text(product.description.orEmpty())); card.addView(text("₹${product.price}", 18f))
                    card.addView(button("Buy / Pay") { selectedProduct = product; showCheckout(product) })
                    content.addView(card)
                }
            } catch(e: Exception) { content.addView(text("Store error: ${e.message}")) }
        }
        content.addView(button("Back") { showHome() })
    }

    private fun showCheckout(product: Product) {
        base("Checkout")
        selectedOrder = null
        selectedProofUri = null
        content.addView(text(product.title, 21f)); content.addView(text(product.description.orEmpty())); content.addView(text("Amount: ₹${product.price}", 19f))
        content.addView(text("UPI ID: ${SupabaseConfig.UPI_ID}"))
        val qr = ImageView(this).apply { adjustViewBounds = true; setPadding(20, 10, 20, 10) }
        try { qr.setImageBitmap(makeQr("upi://pay?pa=${SupabaseConfig.UPI_ID}&pn=${URLEncoder.encode(SupabaseConfig.STORE_NAME, "UTF-8")}&am=${product.price}&cu=INR")) } catch(_: Exception) {}
        content.addView(qr, LinearLayout.LayoutParams(-1, 420))
        content.addView(button("Open UPI App") { openUpi(product) })
        content.addView(text("1. Pay exact amount. 2. Enter UTR. 3. Optionally attach payment screenshot. 4. Submit for manual review."))
        val utr = field("UTR / Transaction ID")
        content.addView(utr)
        val proof = text("No screenshot selected")
        content.addView(button("Choose payment screenshot") { pickProof.launch(arrayOf("image/*")) })
        content.addView(proof)
        content.addView(button("Create Order & Submit for Review") {
            lifecycleScope.launch {
                try {
                    if (utr.text.toString().trim().length < 4) throw IllegalArgumentException("Enter a valid UTR")
                    val order = SupabaseRepository.createOrder(product)
                    val payment = SupabaseRepository.createPayment(order)
                    val uri = selectedProofUri
                    if (uri != null) {
                        val bytes = withContext(Dispatchers.IO) { contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("Cannot read screenshot") }
                        val ext = contentResolver.getType(uri)?.substringAfterLast('/')?.takeIf { it.isNotBlank() } ?: "jpg"
                        SupabaseRepository.uploadPaymentProof(order.id, payment.id, bytes, ext)
                    }
                    SupabaseRepository.submitPaymentForReview(order.id, utr.text.toString().trim())
                    Toast.makeText(this@MainActivity, "Order submitted for review", Toast.LENGTH_LONG).show()
                    showOrders()
                } catch(e: Exception) { Toast.makeText(this@MainActivity, "Submit failed: ${e.message}", Toast.LENGTH_LONG).show() }
            }
        })
        content.addView(button("Back") { showStore() })
    }

    private fun openUpi(product: Product) {
        val uri = Uri.parse("upi://pay?pa=${SupabaseConfig.UPI_ID}&pn=${Uri.encode(SupabaseConfig.STORE_NAME)}&am=${product.price}&cu=INR")
        try { startActivity(Intent(Intent.ACTION_VIEW, uri)) } catch(_: Exception) { Toast.makeText(this, "No UPI app found", Toast.LENGTH_SHORT).show() }
    }

    private fun showOrders() {
        base("My Orders")
        lifecycleScope.launch {
            try {
                val orders = SupabaseRepository.myOrders()
                if (orders.isEmpty()) content.addView(text("No orders yet."))
                orders.forEach { order ->
                    content.addView(text("${order.product_title}\n₹${order.amount}\nStatus: ${order.status}\n${order.created_at.orEmpty()}", 16f))
                    if (order.status == "approved") content.addView(button("View Delivery") { showDelivery(order) })
                    if (order.status == "under_review" || order.status == "rejected") {
                        val payment = runCatching { SupabaseRepository.paymentForOrder(order.id) }.getOrNull()
                        if (payment?.admin_note != null) content.addView(text("Admin note: ${payment.admin_note}"))
                    }
                }
            } catch(e: Exception) { content.addView(text("Orders error: ${e.message}")) }
        }
        content.addView(button("Back") { showHome() })
    }

    private fun showDelivery(order: Order) {
        base("Delivery")
        content.addView(text("${order.product_title}", 20f))
        content.addView(text("Only approved orders can reveal delivery details."))
        lifecycleScope.launch {
            try { val data = SupabaseRepository.approvedDelivery(order.id); content.addView(text(data ?: "Delivery data not available." , 17f)) }
            catch(e: Exception) { content.addView(text("Delivery error: ${e.message}")) }
        }
        content.addView(button("Back") { showOrders() })
    }

    private fun showAccount() {
        base("Account")
        lifecycleScope.launch {
            val p = runCatching { SupabaseRepository.profile() }.getOrNull()
            content.addView(text("Name: ${p?.full_name ?: "-"}")); content.addView(text("Phone: ${p?.phone ?: "-"}")); content.addView(text("Role: ${p?.role ?: "customer"}"))
        }
        content.addView(button("Back") { showHome() })
    }

    private fun showAdmin() {
        base("Admin Panel")
        lifecycleScope.launch {
            try {
                val p = SupabaseRepository.profile()
                if (p?.role != "admin") { content.addView(text("Admin access denied.")); return@launch }
                content.addView(text("Manual payment verification", 19f))
                val reviews = SupabaseRepository.reviewOrders()
                if (reviews.isEmpty()) content.addView(text("No orders under review."))
                reviews.forEach { order -> adminOrderCard(order) }
                content.addView(text("Product management", 19f))
                content.addView(button("Add Product") { showAddProduct() })
                val products = SupabaseRepository.adminProducts()
                products.forEach { product ->
                    val row = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.HORIZONTAL }
                    row.addView(text("${product.title} • ₹${product.price}", 15f), LinearLayout.LayoutParams(0, -2, 1f))
                    row.addView(button(if (product.status == "active") "Disable" else "Enable") {
                        lifecycleScope.launch { runCatching { SupabaseRepository.setProductStatus(product.id, if (product.status == "active") "inactive" else "active") }; showAdmin() }
                    })
                    content.addView(row)
                }
            } catch(e: Exception) { content.addView(text("Admin error: ${e.message}")) }
        }
        content.addView(button("Back") { showHome() })
    }

    private fun adminOrderCard(order: Order) {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(10, 10, 10, 10) }
        box.addView(text("${order.product_title} • ₹${order.amount}"))
        lifecycleScope.launch {
            val payment = runCatching { SupabaseRepository.paymentForOrder(order.id) }.getOrNull()
            val proofs = runCatching { SupabaseRepository.proofsForOrder(order.id) }.getOrDefault(emptyList())
            box.addView(text("Order: ${order.id}\nUTR: ${payment?.utr ?: "-"}\nPayment: ${payment?.status ?: "-"}"))
            proofs.firstOrNull()?.let { proof ->
                box.addView(button("Open payment screenshot") {
                    lifecycleScope.launch {
                        try { currentProofUrl = SupabaseRepository.signedProofUrl(proof.storage_path); startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(currentProofUrl))) }
                        catch(e: Exception) { Toast.makeText(this@MainActivity, "Proof open failed: ${e.message}", Toast.LENGTH_LONG).show() }
                    }
                })
            }
            box.addView(button("APPROVE") {
                lifecycleScope.launch { try { SupabaseRepository.approveOrder(order.id); Toast.makeText(this@MainActivity, "Approved", Toast.LENGTH_SHORT).show(); showAdmin() } catch(e: Exception) { Toast.makeText(this@MainActivity, "Approve failed: ${e.message}", Toast.LENGTH_LONG).show() } }
            })
            box.addView(button("REJECT") {
                val reason = field("Reason")
                AlertDialog.Builder(this@MainActivity).setTitle("Reject order").setView(reason).setPositiveButton("Reject") { _, _ ->
                    lifecycleScope.launch { try { SupabaseRepository.rejectOrder(order.id, reason.text.toString().trim()); showAdmin() } catch(e: Exception) { Toast.makeText(this@MainActivity, "Reject failed: ${e.message}", Toast.LENGTH_LONG).show() } }
                }.setNegativeButton("Cancel", null).show()
            })
        }
        content.addView(box)
    }

    private fun showAddProduct() {
        base("Add Product")
        val title = field("Title"); val desc = field("Description"); val price = field("Price"); val image = field("Image URL (optional)"); val delivery = field("Delivery data (keep sensitive data minimal)")
        content.addView(title); content.addView(desc); content.addView(price); content.addView(image); content.addView(delivery)
        content.addView(button("Save product") {
            lifecycleScope.launch {
                try { SupabaseRepository.addProduct(title.text.toString().trim(), desc.text.toString().trim(), price.text.toString().toDouble(), image.text.toString().trim().ifBlank { null }, delivery.text.toString().trim().ifBlank { null }); Toast.makeText(this@MainActivity, "Product added", Toast.LENGTH_SHORT).show(); showAdmin() }
                catch(e: Exception) { Toast.makeText(this@MainActivity, "Save failed: ${e.message}", Toast.LENGTH_LONG).show() }
            }
        })
        content.addView(button("Back") { showAdmin() })
    }

    private fun makeQr(data: String): Bitmap {
        val matrix: BitMatrix = MultiFormatWriter().encode(data, BarcodeFormat.QR_CODE, 700, 700)
        val bitmap = Bitmap.createBitmap(700, 700, Bitmap.Config.RGB_565)
        for (x in 0 until 700) for (y in 0 until 700) bitmap.setPixel(x, y, if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
        return bitmap
    }
}
