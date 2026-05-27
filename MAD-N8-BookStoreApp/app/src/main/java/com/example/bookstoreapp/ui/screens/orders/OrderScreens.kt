package com.example.bookstoreapp.ui.screens.orders

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.example.bookstoreapp.ui.components.MainTopAppBar
import com.example.bookstoreapp.ui.navigation.Screen
import com.example.bookstoreapp.ui.viewmodels.OrderViewModel
import com.example.bookstoreapp.data.model.StripePaymentIntentResponse
import java.text.NumberFormat
import java.util.Locale
import java.net.URLEncoder
import kotlinx.coroutines.launch
import com.stripe.android.paymentsheet.PaymentSheet
import com.stripe.android.paymentsheet.PaymentSheetResult
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

@Composable
fun OrderHistoryScreen(navController: NavController, orderViewModel: OrderViewModel = viewModel()) {
    val tabs = listOf("Tất cả", "Chờ thanh toán", "Đang xử lý", "Đang giao", "Hoàn tất", "Đã hủy")
    val pagerState = rememberPagerState(pageCount = { tabs.size })
    val coroutineScope = rememberCoroutineScope()
    val format = NumberFormat.getCurrencyInstance(Locale("vi", "VN"))
    val handleBack = {
        if (!navController.navigateUp()) {
            navController.navigate(Screen.Profile.route) {
                launchSingleTop = true
            }
        }
    }

    val statusQuery = when (pagerState.currentPage) {
        0 -> null
        1 -> "Chờ thanh toán"
        2 -> "Đang xử lý"
        3 -> "Đang giao"
        4 -> "Hoàn tất"
        5 -> "Đã hủy"
        else -> null
    }

    LaunchedEffect(pagerState.currentPage) { orderViewModel.loadOrders(statusQuery) }
    BackHandler(onBack = handleBack)

    Column(modifier = Modifier.fillMaxSize()) {
        MainTopAppBar("Lịch sử đơn hàng", navController, onBack = handleBack)

        ScrollableTabRow(selectedTabIndex = pagerState.currentPage) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = pagerState.currentPage == index,
                    onClick = { coroutineScope.launch { pagerState.animateScrollToPage(index) } },
                    text = { Text(title) }
                )
            }
        }

        HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) {
            if (orderViewModel.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (orderViewModel.orders.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Chưa có đơn hàng nào", color = Color.Gray)
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp)
                ) {
                    items(orderViewModel.orders) { order ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            onClick = { navController.navigate("order_detail/${order.orderId}") }
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Đơn hàng #${order.orderId}", style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        order.status ?: "",
                                        color = when (order.status) {
                                            "Hoàn tất" -> Color(0xFF2E7D32)
                                            "Đã hủy" -> Color.Red
                                            else -> MaterialTheme.colorScheme.primary
                                        }
                                    )
                                }
                                if (order.orderDate != null) {
                                    Text(
                                        "Ngày: ${order.orderDate}",
                                        color = Color.Gray,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                val orderTotal = (order.finalAmount ?: order.totalAmount ?: 0.0) * 100000
                                Text(
                                    "Tổng: ${format.format(orderTotal)}",
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun OrderDetailScreen(navController: NavController, orderId: Int, orderViewModel: OrderViewModel = viewModel()) {
    val context = LocalContext.current
    val format = NumberFormat.getCurrencyInstance(Locale("vi", "VN"))

    LaunchedEffect(orderId) { orderViewModel.loadOrderDetail(orderId) }

    var repayIntentResponse by remember { mutableStateOf<StripePaymentIntentResponse?>(null) }
    var pendingStripePI by remember { mutableStateOf<String?>(null) }

    // Đảm bảo callback lấy được giá trị mới nhất
    val currentPI = androidx.compose.runtime.rememberUpdatedState(pendingStripePI)

    val paymentSheetBuilder = remember {
        PaymentSheet.Builder { paymentResult ->
            when (paymentResult) {
                is PaymentSheetResult.Completed -> {
                    // Gọi API xác nhận chủ động
                    val piId = currentPI.value
                    if (piId != null) {
                        orderViewModel.confirmStripePayment(orderId, piId) {
                            orderViewModel.loadOrderDetail(orderId)
                            orderViewModel.loadOrders()
                        }
                    }
                    Toast.makeText(context, "Thanh toán thành công!", Toast.LENGTH_SHORT).show()
                }
                is PaymentSheetResult.Canceled -> {
                    Toast.makeText(context, "Đã hủy thanh toán", Toast.LENGTH_SHORT).show()
                }
                is PaymentSheetResult.Failed -> {
                    Toast.makeText(context, "Lỗi thanh toán: ${paymentResult.error.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    val paymentSheet = paymentSheetBuilder.build()


    // Khi có dữ liệu PaymentIntent, mở PaymentSheet với cấu hình cho phép hiển thị thẻ đã lưu
    LaunchedEffect(repayIntentResponse) {
        repayIntentResponse?.let { res ->
            val config = PaymentSheet.Configuration.Builder("Bookstore BK")
                .customer(
                    if (res.stripeCustomerId != null && res.ephemeralKey != null) {
                        PaymentSheet.CustomerConfiguration(
                            id = res.stripeCustomerId,
                            ephemeralKeySecret = res.ephemeralKey
                        )
                    } else null
                )
                .build()
            paymentSheet.presentWithPaymentIntent(res.clientSecret, config)
            repayIntentResponse = null
        }
    }

    val detail = orderViewModel.orderDetail

    Column(modifier = Modifier.fillMaxSize()) {
        MainTopAppBar("Chi tiết đơn hàng", navController)

        if (detail == null) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            val canConfirmTransfer = detail.status == "Chờ thanh toán" &&
                (detail.payment?.isBankTransfer == true)
            val canRepayWithStripe = detail.status == "Chờ thanh toán" &&
                (detail.payment?.isStripeCard == true)

            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .weight(1f)
            ) {
                Text("Đơn hàng #${detail.orderId}", style = MaterialTheme.typography.titleLarge)
                Text("Trạng thái: ${detail.status}", color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(8.dp))

                detail.address?.let {
                    Text("Giao đến: ${it.receiverName} - ${it.addressString}", color = Color.Gray)
                }
                detail.payment?.let {
                    Text("Thanh toán: ${it.paymentMethod}", color = Color.Gray)
                }
                detail.shipment?.let {
                    Text("Vận chuyển: ${it.shipmentMethod}", color = Color.Gray)
                }

                if (detail.payment?.isBankTransfer == true) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("Hướng dẫn chuyển khoản", style = MaterialTheme.typography.titleMedium)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("Ngân hàng: Vietcombank", style = MaterialTheme.typography.bodyMedium)
                            Text("Số tài khoản: 0123456789", style = MaterialTheme.typography.bodyMedium)
                            Text("Chủ tài khoản: MAD N8 BookStore", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                if (detail.status == "Chờ thanh toán") {
                                    "Vui lòng nhấn nút Thanh toán VietQR bên dưới để quét mã QR và thanh toán tự động."
                                } else {
                                    "Đơn hàng chuyển khoản này đang được xử lý."
                                },
                                color = Color.Gray,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))
                Text("Sản phẩm:", style = MaterialTheme.typography.titleMedium)

                if (detail.items.isNullOrEmpty()) {
                    Text("Chưa có dữ liệu sản phẩm cho đơn hàng này", color = Color.Gray)
                } else {
                    detail.items.forEach { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (!item.bookImage.isNullOrEmpty()) {
                                AsyncImage(
                                    model = item.fullImageUrl,
                                    contentDescription = null,
                                    modifier = Modifier.size(50.dp),
                                    contentScale = ContentScale.Crop
                                )
                                Spacer(modifier = Modifier.padding(4.dp))
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(item.bookTitle ?: "Sách", maxLines = 1)
                                val itemPrice = (item.bookPrice ?: 0.0) * 100000
                                Text("SL: ${item.quantity ?: 1} × ${format.format(itemPrice)}", color = Color.Gray)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))

                val rawTotal = (detail.totalAmount ?: 0.0) * 100000
                Text("Tổng cộng: ${format.format(rawTotal)}")

                detail.voucher?.let {
                    Text("Giảm giá: ${it.description ?: it.code}", color = Color(0xFF2E7D32))
                }

                val rawFinal = (detail.finalAmount ?: detail.totalAmount ?: 0.0) * 100000
                Text(
                    "Thanh toán: ${format.format(rawFinal)}",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            if (canConfirmTransfer) {
                Button(
                    onClick = {
                        orderViewModel.createPayOSPaymentLink(
                            orderId = orderId,
                            onSuccess = { url ->
                                val encodedUrl = URLEncoder.encode(url, "UTF-8")
                                navController.navigate("payment_webview/$encodedUrl")
                            },
                            onError = { err -> Toast.makeText(context, err, Toast.LENGTH_SHORT).show() }
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    if (orderViewModel.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Text("Thanh toán VietQR (PayOS)")
                    }
                }
            }

            if (canRepayWithStripe) {
                Button(
                    onClick = {
                        orderViewModel.createPaymentIntent(
                            orderId = orderId,
                            onSuccess = { response -> 
                                pendingStripePI = response.paymentIntentId
                                repayIntentResponse = response 
                            },
                            onError = { err -> Toast.makeText(context, err, Toast.LENGTH_SHORT).show() }
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    if (orderViewModel.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Text("Thanh toán lại bằng Thẻ")
                    }
                }
            }

            if (detail.status == "Chờ thanh toán" || detail.status == "Đang xử lý") {
                Button(
                    onClick = {
                        orderViewModel.cancelOrder(orderId) {
                            Toast.makeText(context, "Đã hủy đơn hàng", Toast.LENGTH_SHORT).show()
                            navController.popBackStack()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text("Hủy đơn hàng")
                }
            } else {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}
