package com.example.bookstoreapp.data.api

import com.example.bookstoreapp.data.model.*
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*
import java.util.concurrent.TimeUnit

// ===== Token Manager (Lưu JWT trong memory) =====
object TokenManager {
    var token: String? = null
    var customerId: Int = -1
}

// ===== Auth Interceptor (Tự gắn Bearer Token vào mọi request) =====
class AuthInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): okhttp3.Response {
        val original = chain.request()
        val builder = original.newBuilder()
        TokenManager.token?.let {
            builder.addHeader("Authorization", "Bearer $it")
        }
        return chain.proceed(builder.build())
    }
}

// ===== API Interface — Đầy đủ 38 Endpoint =====
interface BookStoreApiService {

    // ---- AUTH (1-5) ----
    @POST("/api/auth/register")
    suspend fun register(@Body request: RegisterRequest): Response<ApiMessage>

    @POST("/api/auth/login")
    suspend fun login(@Body request: LoginRequest): Response<LoginResponse>

    @POST("/api/auth/forgot-password")
    suspend fun forgotPassword(@Body request: ForgotPasswordRequest): Response<ApiMessage>

    @POST("/api/auth/verify-otp")
    suspend fun verifyOtp(@Body request: OtpRequest): Response<OtpResponse>

    @POST("/api/auth/change-password")
    suspend fun changePassword(@Body request: ChangePasswordRequest): Response<ApiMessage>

    // ---- BOOK (6-13) ----
    @GET("/api/books/categories")
    suspend fun getCategories(): Response<List<Category>>

    @GET("/api/books")
    suspend fun getBooks(
        @Query("categoryId") categoryId: Int? = null
    ): Response<List<Book>>

    @GET("/api/books/for-you")
    suspend fun getBooksForYou(): Response<List<Book>>

    @GET("/api/books/{id}")
    suspend fun getBookDetail(@Path("id") bookId: Int): Response<BookDetailResponse>

    @GET("/api/books/search")
    suspend fun searchBooks(
        @Query("q") query: String
    ): Response<com.google.gson.JsonElement>

    @GET("/api/books/author/{id}")
    suspend fun getAuthor(@Path("id") authorId: Int): Response<Any>

    @GET("/api/books/author/{id}/books")
    suspend fun getBooksByAuthor(@Path("id") authorId: Int): Response<List<Book>>

    @GET("/api/books/publisher/{id}/books")
    suspend fun getBooksByPublisher(@Path("id") publisherId: Int): Response<List<Book>>

    // ---- REVIEW (14-15) - Chức năng: Đánh giá sản phẩm ----
    @GET("/api/review/book/{bookId}")
    suspend fun getReviews(@Path("bookId") bookId: Int): Response<List<ReviewItem>>

    @POST("/api/review")
    suspend fun postReview(@Body request: PostReviewRequest): Response<ReviewSubmissionResponse>

    // ---- AI (16) - Chức năng: Trợ lý ảo (Chatbot) & Voice/Image Search ----
    @POST("/api/ai/chatbot")
    suspend fun chatbot(@Body request: ChatbotRequest): Response<ChatbotResponse>

    @Multipart
    @POST("/api/ai/image-search")
    suspend fun imageSearch(@Part image: okhttp3.MultipartBody.Part): Response<AISearchResponse>

    @Multipart
    @POST("/api/ai/voice-search")
    suspend fun voiceSearch(@Part audio: okhttp3.MultipartBody.Part): Response<AISearchResponse>

    // ---- CART (17-20) ----
    @GET("/api/cart")
    suspend fun getCart(@Query("customerId") customerId: Int): Response<CartResponse>

    @POST("/api/cart/add")
    suspend fun addToCart(@Body request: AddToCartRequest): Response<ApiMessage>

    @PUT("/api/cart/item/{cartItemId}")
    suspend fun updateCartItem(
        @Path("cartItemId") cartItemId: Int,
        @Body request: UpdateQuantityRequest
    ): Response<ApiMessage>

    @DELETE("/api/cart/item/{cartItemId}")
    suspend fun deleteCartItem(@Path("cartItemId") cartItemId: Int): Response<ApiMessage>

    // ---- PROFILE (21-30) ----
    @GET("/api/profile")
    suspend fun getProfile(@Query("customerId") customerId: Int): Response<ProfileResponse>

    @PUT("/api/profile")
    suspend fun updateProfile(@Body request: UpdateProfileRequest): Response<ApiMessage>

    @GET("/api/profile/address")
    suspend fun getAddresses(@Query("customerId") customerId: Int): Response<List<AddressItem>>

    @POST("/api/profile/address")
    suspend fun createAddress(@Body request: CreateAddressRequest): Response<AddressItem>

    @PUT("/api/profile/address/{id}")
    suspend fun updateAddress(@Path("id") id: Int, @Body request: AddressItem): Response<ApiMessage>

    @DELETE("/api/profile/address/{id}")
    suspend fun deleteAddress(@Path("id") id: Int): Response<ApiMessage>

    @GET("/api/profile/payment")
    suspend fun getPayments(@Query("customerId") customerId: Int): Response<List<PaymentItem>>

    @POST("/api/profile/payment")
    suspend fun createPayment(@Body request: CreatePaymentRequest): Response<PaymentItem>

    @PUT("/api/profile/payment/{id}")
    suspend fun updatePayment(@Path("id") id: Int, @Body request: PaymentItem): Response<ApiMessage>

    @DELETE("/api/profile/payment/{id}")
    suspend fun deletePayment(@Path("id") id: Int): Response<ApiMessage>

    // ---- CHECKOUT DATA (31-33) ----
    @GET("/api/checkout-data/vouchers")
    suspend fun getVouchers(): Response<List<VoucherItem>>

    @GET("/api/checkout-data/shipments")
    suspend fun getShipments(): Response<List<ShipmentItem>>

    @POST("/api/checkout-data/vouchers/validate")
    suspend fun validateVoucher(@Body request: ValidateVoucherRequest): Response<ValidateVoucherResponse>

    // ---- ORDER (34-38) - Chức năng: Quản lý đơn hàng & Thanh toán ----
    @POST("/api/order/checkout")
    suspend fun checkout(@Body request: CheckoutRequest): Response<OrderResponse>

    @POST("/api/order/buy-now")
    suspend fun buyNow(@Body request: BuyNowRequest): Response<OrderResponse>

    @GET("/api/order")
    suspend fun getOrders(
        @Query("customerId") customerId: Int,
        @Query("status") status: String? = null
    ): Response<List<OrderItem>>

    @GET("/api/order/{orderId}")
    suspend fun getOrderDetail(@Path("orderId") orderId: Int): Response<OrderDetailResponse>

    @PUT("/api/order/{orderId}/cancel")
    suspend fun cancelOrder(@Path("orderId") orderId: Int): Response<ApiMessage>

    @POST("/api/order/repay")
    suspend fun repayOrder(@Body request: Map<String, @JvmSuppressWildcards Any>): Response<OrderResponse>

    // ---- PAYMENT EXTERNAL (STRIPE, PAYOS, VNPAY) - Chức năng: Thanh toán ----
    @POST("/api/stripe/create-setup-intent")
    suspend fun createSetupIntent(@Body request: CreateSetupIntentRequest): Response<SetupIntentResponse>

    @POST("/api/stripe/save-card")
    suspend fun saveStripeCard(@Body request: SaveStripeCardRequest): Response<ApiMessage>

    @POST("/api/stripe/create-payment-intent")
    suspend fun createStripePaymentIntent(@Body request: StripePaymentIntentRequest): Response<StripePaymentIntentResponse>

    @POST("/api/stripe/confirm-payment")
    suspend fun confirmStripePayment(@Body request: ConfirmStripePaymentRequest): Response<ApiMessage>

    @POST("/api/payos/create-payment-link")
    suspend fun createPayOSPaymentLink(@Body request: PayOSPaymentRequest): Response<PayOSPaymentResponse>
}

// ===== Retrofit Client Singleton =====
object RetrofitClient {
    val BASE_URL = if (android.os.Build.FINGERPRINT.contains("generic") || android.os.Build.MODEL.contains("Emulator") || android.os.Build.PRODUCT.contains("sdk")) {
        "http://10.0.2.2:3000" // IP dành cho Android Emulator
    } else {
        "http://127.0.0.1:3000" // IP dành cho thiết bị thật (yêu cầu adb reverse)
    }

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor())
            .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY })
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    val api: BookStoreApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(BookStoreApiService::class.java)
    }
}
