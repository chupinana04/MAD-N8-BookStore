package com.example.bookstoreapp.data.model

import com.google.gson.annotations.SerializedName
import java.text.Normalizer
import java.util.Locale

// ===================== AUTH =====================
data class LoginRequest(val email: String, val password: String)
data class LoginResponse(val token: String, val user: UserInfo)
data class UserInfo(
    @SerializedName("customerID") val customerId: Int,
    val email: String,
    val fullName: String? = null
)

data class RegisterRequest(val email: String, val password: String, val confirmPassword: String)
data class OtpRequest(val email: String, val otpCode: String)
data class OtpResponse(val resetToken: String? = null, val message: String? = null)
data class ForgotPasswordRequest(val email: String)
data class ChangePasswordRequest(
    val oldPassword: String? = null,
    val newPassword: String,
    val confirmPassword: String,
    val resetToken: String? = null
)
data class ApiMessage(val message: String? = null, val error: String? = null)

// ===================== BOOK =====================
data class Category(
    @SerializedName("categoryID") val categoryId: Int,
    val categoryName: String
)

data class Book(
    @SerializedName("bookID") val bookId: Int,
    val title: String,
    val author: String? = null,
    val price: Double,
    val avgRating: Double? = 0.0,
    val reviewCount: Int? = 0,
    val soldCount: Int? = 0,
    val variantLabel: String? = null,
    val description: String? = null,
    val language: String? = null,
    val pageCount: Int? = null,
    @SerializedName("BookImages") val images: List<BookImage>? = null,
    val primaryImage: String? = null
) {
    val primaryImageUrl: String
        get() {
            val url = primaryImage 
                ?: images?.firstOrNull { it.isPrimary == true }?.imageURL
                ?: images?.firstOrNull()?.imageURL 
                ?: ""
            
            if (url.startsWith("//")) return "https:$url"
            return if (url.startsWith("/")) com.example.bookstoreapp.data.api.RetrofitClient.BASE_URL + url else url
        }
}

data class BookImage(
    @SerializedName("imageURL") val imageURL: String,
    val isPrimary: Boolean? = false
)

data class AuthorInfo(
    @SerializedName("authorID") val authorID: Int? = null,
    @SerializedName("authorName") val fullName: String? = null,
    @SerializedName("biography") val bio: String? = null,
    val avatar: String? = null
)

data class PaginatedBooks(
    val data: List<Book>,
    val pagination: PaginationInfo? = null
)
data class PaginationInfo(
    val page: Int,
    val limit: Int,
    val total: Int? = null,
    val totalPages: Int? = null
) {
    val computedTotalPages: Int
        get() = totalPages ?: if (total != null && limit > 0) Math.ceil(total.toDouble() / limit).toInt() else 1
}

data class BookDetailResponse(
    val book: Book,
    val avgRating: Double? = 0.0,
    val totalReviews: Int? = 0,
    val soldCount: Int? = 0,
    val top3Reviews: List<ReviewItem>? = emptyList(),
    val similarSource: String? = null,
    val similarBooks: List<Book>? = emptyList()
)

// ===================== REVIEW =====================
data class ReviewItem(
    @SerializedName("reviewID") val reviewId: Int? = null,
    val rating: Double,
    val comment: String,
    @SerializedName("idCustomer") val customerId: Int? = null,
    @SerializedName("Customer") val customer: ReviewCustomer? = null,
    val createdAt: String? = null
)

data class ReviewCustomer(val fullName: String?)

data class PostReviewRequest(
    val bookId: Int,
    val rating: Int,
    val comment: String
)

data class ReviewSubmissionResponse(
    val message: String? = null,
    val updated: Boolean = false,
    val review: ReviewItem? = null
)

// ===================== CART =====================
data class CartResponse(
    @SerializedName("cartID") val cartId: Int,
    val items: List<CartItemResponse>,
    val totalAmount: Double
)

data class CartItemResponse(
    @SerializedName("cartItemID") val cartItemId: Int,
    val quantity: Int,
    @SerializedName("idBook") val idBook: Int? = null,
    @SerializedName("Book") val book: Book? = null
) {
    val bookId: Int get() = book?.bookId ?: idBook ?: 0
    val bookTitle: String get() = book?.title ?: "Sách"
    val bookPrice: Double get() = book?.price ?: 0.0
    val bookImage: String? get() = book?.primaryImageUrl
    val fullImageUrl: String get() = book?.primaryImageUrl ?: ""
}

data class AddToCartRequest(
    val customerId: Int,
    val bookId: Int,
    val quantity: Int = 1
)

data class UpdateQuantityRequest(val quantity: Int)

// ===================== PROFILE =====================
data class ProfileResponse(
    @SerializedName("customerID") val customerId: Int? = null,
    val fullName: String? = null,
    val email: String? = null,
    val phoneNumber: String? = null
)

data class UpdateProfileRequest(
    val customerId: Int,
    val fullName: String,
    val phoneNumber: String
)

data class AddressItem(
    @SerializedName("addressID") val addressId: Int? = null,
    val receiverName: String,
    val addressString: String,
    @SerializedName("idCustomer") val customerId: Int? = null
)

data class CreateAddressRequest(
    val customerId: Int,
    val receiverName: String,
    val addressString: String
)


data class PaymentItem(
    @SerializedName("paymentID") val paymentId: Int? = null,
    val paymentMethod: String,
    val status: String? = null,
    @SerializedName("idCustomer") val customerId: Int? = null,
    val vnpToken: String? = null
) {
    private val normalizedMethod: String
        get() = Normalizer.normalize(paymentMethod.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace("\\p{M}+".toRegex(), "")
            .replace("đ", "d")

    val isCashOnDelivery: Boolean
        get() = normalizedMethod.contains("cod")

    val isBankTransfer: Boolean
        get() = normalizedMethod.contains("chuyen khoan") || normalizedMethod.contains("bank transfer")

    val isStripeCard: Boolean
        get() = !vnpToken.isNullOrBlank() || normalizedMethod.contains("the") || normalizedMethod.contains("visa") || normalizedMethod.contains("mastercard") || normalizedMethod.contains("stripe")

    val isSupportedInCheckout: Boolean
        get() = isCashOnDelivery || isBankTransfer || isStripeCard

    val displayStatus: String
        get() = status ?: if (isSupportedInCheckout) "Hoạt động" else "Chưa hỗ trợ"

    val checkoutHint: String
        get() = when {
            isCashOnDelivery -> "Đơn sẽ chuyển sang Đang xử lý ngay sau khi đặt hàng."
            isBankTransfer -> "Đơn sẽ ở Chờ thanh toán cho đến khi bạn xác nhận đã chuyển khoản."
            isStripeCard -> "Thanh toán an toàn qua cổng Stripe."
            else -> "Phương thức này chưa được hỗ trợ hoàn tất trong ứng dụng."
        }
}

data class CreatePaymentRequest(
    val customerId: Int,
    val paymentMethod: String
)

// ===================== CHECKOUT DATA =====================
data class VoucherItem(
    @SerializedName("voucherID") val voucherId: Int,
    val code: String,
    val description: String? = null,
    val type: String? = null,
    val discountValue: Double? = null,
    val minOrderValue: Double? = null,
    val expiryDate: String? = null,
    val usageLimit: Int? = null
)

data class ShipmentItem(
    @SerializedName("shipmentID") val shipmentId: Int,
    val shipmentMethod: String,
    val estimatedDate: String? = null,
    val status: String? = null
)

data class ValidateVoucherRequest(val voucherCode: String, val totalAmount: Double)
data class ValidateVoucherResponse(
    val isValid: Boolean,
    val finalAmount: Double? = null,
    val discountAmount: Double? = null,
    val message: String? = null
)

// ===================== ORDER =====================
data class CheckoutRequest(
    val customerId: Int,
    val addressId: Int,
    val paymentId: Int,
    val shipmentId: Int,
    val voucherId: Int? = null,
    val selectedCartItemIds: List<Int>? = null
)

data class BuyNowRequest(
    val customerId: Int,
    val addressId: Int,
    val paymentId: Int,
    val shipmentId: Int,
    val voucherId: Int? = null,
    val bookId: Int,
    val quantity: Int
)

data class OrderResponse(
    @SerializedName(value = "orderId", alternate = ["orderID"]) val orderId: Int? = null,
    val message: String? = null,
    val status: String? = null,
    val paymentUrl: String? = null
)

data class OrderItem(
    @SerializedName("orderID") val orderId: Int,
    val orderDate: String? = null,
    val totalAmount: Double? = null,
    val finalAmount: Double? = null,
    val status: String? = null,
    val items: List<OrderBookItem>? = null
)

data class OrderBookItem(
    val bookTitle: String? = null,
    val bookPrice: Double? = null,
    val quantity: Int? = null,
    val bookImage: String? = null
) {
    val fullImageUrl: String
        get() = if (bookImage?.startsWith("//") == true) "https:$bookImage"
        else if (bookImage?.startsWith("/") == true) com.example.bookstoreapp.data.api.RetrofitClient.BASE_URL + bookImage 
        else bookImage ?: ""
}

data class OrderDetailResponse(
    @SerializedName("orderID") val orderId: Int,
    val orderDate: String? = null,
    val totalAmount: Double? = null,
    val finalAmount: Double? = null,
    val status: String? = null,
    val items: List<OrderBookItem>? = null,
    val address: AddressItem? = null,
    val payment: PaymentItem? = null,
    val shipment: ShipmentItem? = null,
    val voucher: VoucherItem? = null
)

// ===================== CHATBOT =====================
data class ChatbotRequest(val userMessage: String)
data class ChatbotResponse(val reply: String? = null, val message: String? = null)

// ===================== AI SEARCH =====================
data class AISearchResponse(
    @SerializedName(value = "recognizedText", alternate = ["ai_detected_title"])
    val recognizedText: String?,
    val results: List<Book>
)

// ===================== STRIPE =====================
data class CreateSetupIntentRequest(val customerId: Int)
data class SetupIntentResponse(val clientSecret: String)

data class SaveStripeCardRequest(val customerId: Int)

data class StripePaymentIntentRequest(val orderId: Int, val customerId: Int)
data class StripePaymentIntentResponse(
    val clientSecret: String,
    val paymentIntentId: String? = null,
    val stripeCustomerId: String? = null,
    val ephemeralKey: String? = null
)
data class ConfirmStripePaymentRequest(val orderId: Int, val paymentIntentId: String)
data class PayOSPaymentRequest(val orderId: Int)
data class PayOSPaymentResponse(val message: String, val paymentUrl: String, val orderId: Int)
