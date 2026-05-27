package com.example.bookstoreapp.ui.viewmodels

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.bookstoreapp.data.api.RetrofitClient
import com.example.bookstoreapp.data.api.TokenManager
import com.example.bookstoreapp.data.model.*
import kotlinx.coroutines.launch

class OrderViewModel : ViewModel() {
    private val api = RetrofitClient.api

    var vouchers by mutableStateOf<List<VoucherItem>>(emptyList())
    var shipments by mutableStateOf<List<ShipmentItem>>(emptyList())
    var selectedVoucher by mutableStateOf<VoucherItem?>(null)
    var selectedShipment by mutableStateOf<ShipmentItem?>(null)
    var selectedAddress by mutableStateOf<AddressItem?>(null)
    var selectedPayment by mutableStateOf<PaymentItem?>(null)
    var validateResult by mutableStateOf<ValidateVoucherResponse?>(null)

    var orders by mutableStateOf<List<OrderItem>>(emptyList())
    var orderDetail by mutableStateOf<OrderDetailResponse?>(null)
    var checkoutResult by mutableStateOf<OrderResponse?>(null)
    var lastPaymentUrl by mutableStateOf<String?>(null)
    var message by mutableStateOf<String?>(null)
    var isLoading by mutableStateOf(false)

    fun loadVouchers() {
        viewModelScope.launch {
            try {
                val response = api.getVouchers()
                if (response.isSuccessful) vouchers = response.body() ?: emptyList()
            } catch (_: Exception) {}
        }
    }

    fun loadShipments() {
        viewModelScope.launch {
            try {
                val response = api.getShipments()
                if (response.isSuccessful) shipments = response.body() ?: emptyList()
            } catch (_: Exception) {}
        }
    }

    fun validateVoucher(code: String, totalAmount: Double) {
        viewModelScope.launch {
            try {
                val response = api.validateVoucher(ValidateVoucherRequest(code, totalAmount))
                if (response.isSuccessful) validateResult = response.body()
                else validateResult = ValidateVoucherResponse(false, message = "Mã không hợp lệ")
            } catch (_: Exception) {}
        }
    }

    /**
     * Thanh toán giỏ hàng (Checkout).
     * Chức năng: Thanh toán
     * Thu thập thông tin địa chỉ, phương thức thanh toán, vận chuyển, voucher
     * để gọi API thanh toán và tạo đơn hàng. Trả về OrderResponse chứa trạng thái và URL thanh toán (nếu có).
     */
    fun checkout(
        addressId: Int,
        paymentId: Int,
        shipmentId: Int,
        voucherId: Int?,
        selectedCartItemIds: List<Int>,
        onSuccess: (OrderResponse) -> Unit
    ) {
        viewModelScope.launch {
            isLoading = true
            try {
                val request = CheckoutRequest(
                    customerId = TokenManager.customerId,
                    addressId = addressId,
                    paymentId = paymentId,
                    shipmentId = shipmentId,
                    voucherId = voucherId,
                    selectedCartItemIds = selectedCartItemIds
                )
                val response = api.checkout(request)
                if (response.isSuccessful && response.body() != null) {
                    checkoutResult = response.body()
                    lastPaymentUrl = checkoutResult?.paymentUrl
                    onSuccess(checkoutResult!!)
                } else {
                    message = "Đặt hàng thất bại"
                }
            } catch (e: Exception) {
                message = e.message
            }
            isLoading = false
        }
    }

    fun buyNow(
        addressId: Int,
        paymentId: Int,
        shipmentId: Int,
        voucherId: Int?,
        bookId: Int,
        quantity: Int,
        onSuccess: (OrderResponse) -> Unit
    ) {
        viewModelScope.launch {
            isLoading = true
            try {
                val request = BuyNowRequest(TokenManager.customerId, addressId, paymentId, shipmentId, voucherId, bookId, quantity)
                val response = api.buyNow(request)
                if (response.isSuccessful && response.body() != null) {
                    checkoutResult = response.body()
                    lastPaymentUrl = checkoutResult?.paymentUrl
                    onSuccess(checkoutResult!!)
                } else {
                    message = "Mua hàng thất bại"
                }
            } catch (e: Exception) {
                message = e.message
            }
            isLoading = false
        }
    }

    /**
     * Lấy danh sách lịch sử đơn hàng của User.
     * Chức năng: Quản lý đơn hàng
     * Lọc theo status (nếu có).
     */
    fun loadOrders(status: String? = null) {
        viewModelScope.launch {
            isLoading = true
            try {
                val response = api.getOrders(TokenManager.customerId, status)
                if (response.isSuccessful) orders = response.body() ?: emptyList()
            } catch (_: Exception) {}
            isLoading = false
        }
    }

    /**
     * Tải chi tiết một đơn hàng cụ thể.
     * Chức năng: Quản lý đơn hàng
     */
    fun loadOrderDetail(orderId: Int) {
        viewModelScope.launch {
            orderDetail = null
            try {
                val response = api.getOrderDetail(orderId)
                if (response.isSuccessful) orderDetail = response.body()
            } catch (_: Exception) {}
        }
    }

    /**
     * Hủy đơn hàng.
     * Chức năng: Quản lý đơn hàng
     * Gửi yêu cầu hủy và cập nhật lại danh sách nếu thành công.
     * Hỗ trợ tự động hoàn tiền nếu đã thanh toán (VNPay/Stripe).
     */
    fun cancelOrder(orderId: Int, onDone: () -> Unit) {
        viewModelScope.launch {
            try {
                val response = api.cancelOrder(orderId)
                if (response.isSuccessful) {
                    loadOrders()
                    onDone()
                } else {
                    message = "Không thể hủy đơn hàng này"
                }
            } catch (e: Exception) {
                message = e.message
            }
        }
    }

    /**
     * Yêu cầu thanh toán lại cho đơn hàng (Repay).
     * Chức năng: Thanh toán
     * Phục vụ cho các đơn chuyển khoản VietQR chưa thanh toán (tạo lại link PayOS).
     */
    fun repayOrder(orderId: Int, onDone: (OrderResponse?) -> Unit) {
        viewModelScope.launch {
            isLoading = true
            try {
                val response = api.repayOrder(mapOf("orderId" to orderId))
                if (response.isSuccessful) {
                    val body = response.body()
                    lastPaymentUrl = body?.paymentUrl
                    body?.status?.let { newStatus ->
                        orderDetail = orderDetail?.copy(status = newStatus)
                        orders = orders.map { order ->
                            if (order.orderId == orderId) order.copy(status = newStatus) else order
                        }
                    }
                    loadOrderDetail(orderId)
                    loadOrders()
                    onDone(body)
                } else {
                    message = "Không thể xác nhận thanh toán cho đơn hàng này"
                    onDone(null)
                }
            } catch (e: Exception) {
                message = e.message
                onDone(null)
            }
            isLoading = false
        }
    }

    /**
     * Khởi tạo giao dịch thanh toán quốc tế qua Stripe.
     * Trả về PaymentIntent (clientSecret) để Android SDK xử lý.
     */
    fun createPaymentIntent(orderId: Int, onSuccess: (StripePaymentIntentResponse) -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            isLoading = true
            try {
                val response = api.createStripePaymentIntent(StripePaymentIntentRequest(orderId, TokenManager.customerId))
                if (response.isSuccessful && response.body() != null) {
                    onSuccess(response.body()!!)
                } else {
                    onError("Không thể khởi tạo thanh toán Stripe")
                }
            } catch (e: Exception) {
                onError(e.message ?: "Lỗi kết nối Stripe")
            }
            isLoading = false
        }
    }

    /**
     * Xác nhận thanh toán qua Stripe thành công với backend.
     */
    fun confirmStripePayment(orderId: Int, paymentIntentId: String, onDone: () -> Unit) {
        viewModelScope.launch {
            try {
                val response = api.confirmStripePayment(ConfirmStripePaymentRequest(orderId, paymentIntentId))
                if (!response.isSuccessful) {
                    android.util.Log.e("StripeConfirm", "Xác nhận thất bại: ${response.errorBody()?.string()}")
                } else {
                    android.util.Log.d("StripeConfirm", "Xác nhận thành công cho đơn hàng $orderId")
                }
                onDone()
            } catch (e: Exception) {
                android.util.Log.e("StripeConfirm", "Lỗi kết nối khi xác nhận: ${e.message}")
                onDone()
            }
        }
    }

    /**
     * Khởi tạo giao dịch thanh toán nội địa VietQR qua PayOS.
     * Trả về Payment URL dạng QR code để WebView load.
     */
    fun createPayOSPaymentLink(orderId: Int, onSuccess: (String) -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            isLoading = true
            try {
                val response = api.createPayOSPaymentLink(PayOSPaymentRequest(orderId))
                if (response.isSuccessful && response.body() != null) {
                    onSuccess(response.body()!!.paymentUrl)
                } else {
                    onError("Không thể tạo link thanh toán VietQR")
                }
            } catch (e: Exception) {
                onError(e.message ?: "Lỗi kết nối PayOS")
            }
            isLoading = false
        }
    }
}
