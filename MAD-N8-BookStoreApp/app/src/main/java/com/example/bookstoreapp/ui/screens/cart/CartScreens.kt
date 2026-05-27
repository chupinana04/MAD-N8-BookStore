package com.example.bookstoreapp.ui.screens.cart

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.example.bookstoreapp.data.model.PaymentItem
import com.example.bookstoreapp.ui.components.MainTopAppBar
import com.example.bookstoreapp.ui.navigation.Screen
import com.example.bookstoreapp.ui.viewmodels.CartViewModel
import com.example.bookstoreapp.ui.viewmodels.OrderViewModel
import com.stripe.android.paymentsheet.PaymentSheet
import com.stripe.android.paymentsheet.PaymentSheetResult
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.bookstoreapp.data.model.StripePaymentIntentResponse
import java.text.NumberFormat
import java.util.Locale
import java.net.URLEncoder

@Composable
fun CartScreen(navController: NavController, cartViewModel: CartViewModel = viewModel()) {
    val format = NumberFormat.getCurrencyInstance(Locale("vi", "VN"))

    LaunchedEffect(Unit) { cartViewModel.loadCart() }

    Scaffold(
        bottomBar = {
            if (cartViewModel.cartItems.isNotEmpty()) {
                Surface(
                    shadowElevation = 8.dp,
                    tonalElevation = 4.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Tổng", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
                            Text(
                                format.format(cartViewModel.selectedTotalPrice * 100000),
                                style = MaterialTheme.typography.titleLarge
                            )
                            Text(
                                text = if (cartViewModel.hasSelectedItems) {
                                    "Sẵn sàng thanh toán"
                                } else {
                                    "Chọn sản phẩm để bật thanh toán"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray
                            )
                        }
                        Button(
                            onClick = { navController.navigate(Screen.Checkout.route) },
                            enabled = cartViewModel.hasSelectedItems,
                            colors = ButtonDefaults.buttonColors(
                                disabledContainerColor = Color(0xFFDEDEDE),
                                disabledContentColor = Color(0xFF8A8A8A)
                            ),
                            modifier = Modifier.alpha(if (cartViewModel.hasSelectedItems) 1f else 0.75f)
                        ) {
                            Text("Thanh toán")
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
        ) {
            Text(
                "Giỏ hàng của bạn",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(top = 16.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))

            if (cartViewModel.isLoading) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (cartViewModel.cartItems.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Giỏ hàng trống", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    items(cartViewModel.cartItems) { item ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable { navController.navigate("product_detail/${item.bookId}") }
                        ) {
                            Row(
                                modifier = Modifier.padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = cartViewModel.isSelected(item.cartItemId),
                                    onCheckedChange = { checked ->
                                        cartViewModel.toggleSelection(item.cartItemId, checked)
                                    }
                                )
                                if (!item.bookImage.isNullOrEmpty()) {
                                    AsyncImage(
                                        model = item.fullImageUrl,
                                        contentDescription = null,
                                        modifier = Modifier.size(60.dp),
                                        contentScale = ContentScale.Crop
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                }
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(horizontal = 8.dp)
                                ) {
                                    Text(item.bookTitle, style = MaterialTheme.typography.titleMedium, maxLines = 2)
                                    Text(
                                        format.format(item.bookPrice * 100000),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(top = 4.dp)
                                    ) {
                                        OutlinedButton(
                                            onClick = {
                                                if (item.quantity > 1) {
                                                    cartViewModel.updateQuantity(item.cartItemId, item.quantity - 1)
                                                }
                                            },
                                            enabled = item.quantity > 1,
                                            modifier = Modifier.size(32.dp),
                                            contentPadding = PaddingValues(0.dp)
                                        ) {
                                            Text("-")
                                        }
                                        Text(item.quantity.toString(), modifier = Modifier.padding(horizontal = 12.dp))
                                        OutlinedButton(
                                            onClick = { cartViewModel.updateQuantity(item.cartItemId, item.quantity + 1) },
                                            modifier = Modifier.size(32.dp),
                                            contentPadding = PaddingValues(0.dp)
                                        ) {
                                            Text("+")
                                        }
                                    }
                                }
                                IconButton(onClick = { cartViewModel.deleteItem(item.cartItemId) }) {
                                    Icon(
                                        Icons.Outlined.Delete,
                                        contentDescription = "Xóa",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CheckoutScreen(
    navController: NavController,
    orderViewModel: OrderViewModel = viewModel(),
    cartViewModel: CartViewModel = viewModel()
) {
    val context = LocalContext.current
    val selectedPayment = orderViewModel.selectedPayment
    val canCheckout = !orderViewModel.isLoading &&
        cartViewModel.hasSelectedItems &&
        orderViewModel.selectedAddress != null &&
        selectedPayment != null &&
        selectedPayment.isSupportedInCheckout &&
        orderViewModel.selectedShipment != null
    val checkoutLabel = when {
        selectedPayment?.isCashOnDelivery == true -> "Xác nhận đơn COD"
        selectedPayment?.isBankTransfer == true -> "Tạo đơn chờ thanh toán"
        selectedPayment?.isStripeCard == true -> "Thanh toán bằng thẻ"
        else -> "Xác nhận đặt hàng"
    }
    var paymentIntentResponse by remember { mutableStateOf<StripePaymentIntentResponse?>(null) }
    var pendingStripeOrderId by remember { mutableStateOf<Int?>(null) }
    var pendingStripePI by remember { mutableStateOf<String?>(null) }
    
    // Sử dụng rememberUpdatedState để callback của PaymentSheet luôn lấy được giá trị mới nhất
    val currentOrderId = androidx.compose.runtime.rememberUpdatedState(pendingStripeOrderId)
    val currentPI = androidx.compose.runtime.rememberUpdatedState(pendingStripePI)

    val paymentSheetBuilder = remember {
        PaymentSheet.Builder { paymentResult ->
            when (paymentResult) {
                is PaymentSheetResult.Completed -> {
                    // Gọi API xác nhận chủ động để cập nhật DB ngay lập tức
                    val oId = currentOrderId.value
                    val piId = currentPI.value
                    if (oId != null && piId != null) {
                        orderViewModel.confirmStripePayment(oId, piId) {}
                    }
                    
                    cartViewModel.loadCart()
                    cartViewModel.clearSelection()
                    Toast.makeText(context, "Đặt hàng và thanh toán thành công", Toast.LENGTH_SHORT).show()
                    navController.navigate(Screen.OrderHistory.route)
                }
                is PaymentSheetResult.Canceled -> {
                    cartViewModel.loadCart()
                    cartViewModel.clearSelection()
                    Toast.makeText(context, "Thanh toán chưa hoàn tất. Đơn hàng đã được lưu lại trong mục Chờ thanh toán", Toast.LENGTH_LONG).show()
                    navController.navigate(Screen.OrderHistory.route)
                }
                is PaymentSheetResult.Failed -> {
                    Toast.makeText(context, "Lỗi thanh toán: ${paymentResult.error.message}", Toast.LENGTH_LONG).show()
                    cartViewModel.loadCart()
                    cartViewModel.clearSelection()
                    navController.navigate(Screen.OrderHistory.route)
                }
            }
        }
    }
    val paymentSheet = paymentSheetBuilder.build()

    // Khi có dữ liệu PaymentIntent, mở PaymentSheet với cấu hình cho phép hiển thị thẻ đã lưu
    LaunchedEffect(paymentIntentResponse) {
        paymentIntentResponse?.let { res ->
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
            paymentIntentResponse = null
        }
    }

    LaunchedEffect(Unit) {
        orderViewModel.loadShipments()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        MainTopAppBar("Thanh toán", navController)
        Column(
            modifier = Modifier
                .padding(16.dp)
                .weight(1f)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            Card(modifier = Modifier.fillMaxWidth().clickable { navController.navigate("address_selection") }) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Địa chỉ giao hàng", fontWeight = FontWeight.Bold)
                        Text(
                            orderViewModel.selectedAddress?.let { "${it.receiverName} - ${it.addressString}" }
                                ?: "Chọn địa chỉ",
                            color = Color.Gray,
                            maxLines = 1
                        )
                    }
                    Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = Color.Gray)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Card(modifier = Modifier.fillMaxWidth().clickable { navController.navigate("payment_selection") }) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Phương thức thanh toán", fontWeight = FontWeight.Bold)
                        Text(
                            orderViewModel.selectedPayment?.paymentMethod ?: "Chọn phương thức",
                            color = Color.Gray,
                            maxLines = 1
                        )
                    }
                    Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = Color.Gray)
                }
            }

            selectedPayment?.let { payment ->
                Spacer(modifier = Modifier.height(12.dp))
                PaymentSupportCard(payment = payment)
            }

            Spacer(modifier = Modifier.height(16.dp))
            Card(modifier = Modifier.fillMaxWidth().clickable { navController.navigate(Screen.ShipmentSelection.route) }) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Đơn vị vận chuyển", fontWeight = FontWeight.Bold)
                        Text(
                            orderViewModel.selectedShipment?.shipmentMethod ?: "Chọn đơn vị",
                            color = Color.Gray,
                            maxLines = 1
                        )
                    }
                    Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = Color.Gray)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Card(
                modifier = Modifier.fillMaxWidth().clickable {
                    navController.navigate("voucher_selection/${cartViewModel.selectedTotalPrice}")
                }
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Khuyến mãi / Voucher", fontWeight = FontWeight.Bold)
                        Text(
                            orderViewModel.selectedVoucher?.description ?: "Chọn voucher",
                            color = Color(0xFF2E7D32),
                            maxLines = 1
                        )
                    }
                    Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = Color.Gray)
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
            Text(
                "Đang chọn ${cartViewModel.selectedCartItemIds.size} sản phẩm",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Gray
            )
        }

        Surface(shadowElevation = 8.dp) {
            Button(
                onClick = {
                    orderViewModel.checkout(
                        addressId = orderViewModel.selectedAddress?.addressId ?: 0,
                        paymentId = orderViewModel.selectedPayment?.paymentId ?: 0,
                        shipmentId = orderViewModel.selectedShipment?.shipmentId ?: 0,
                        voucherId = orderViewModel.selectedVoucher?.voucherId,
                        selectedCartItemIds = cartViewModel.selectedCartItemIds
                    ) { result ->
                        if (orderViewModel.selectedPayment?.isStripeCard == true) {
                            result.orderId?.let { oId ->
                                orderViewModel.createPaymentIntent(
                                    orderId = oId,
                                    onSuccess = { response -> 
                                        pendingStripeOrderId = oId
                                        pendingStripePI = response.paymentIntentId
                                        paymentIntentResponse = response 
                                    },
                                    onError = { err ->
                                        Toast.makeText(context, err, Toast.LENGTH_SHORT).show()
                                        cartViewModel.loadCart()
                                        cartViewModel.clearSelection()
                                        navController.navigate(Screen.OrderHistory.route)
                                    }
                                )
                            }
                        } else if (orderViewModel.selectedPayment?.isBankTransfer == true) {
                            // Luồng PayOS cho Chuyển khoản
                            result.orderId?.let { oId ->
                                orderViewModel.createPayOSPaymentLink(
                                    orderId = oId,
                                    onSuccess = { url ->
                                        cartViewModel.loadCart()
                                        cartViewModel.clearSelection()
                                        val encodedUrl = URLEncoder.encode(url, "UTF-8")
                                        navController.navigate("payment_webview/$encodedUrl")
                                    },
                                    onError = { err ->
                                        Toast.makeText(context, err, Toast.LENGTH_SHORT).show()
                                        cartViewModel.loadCart()
                                        cartViewModel.clearSelection()
                                        navController.navigate(Screen.OrderHistory.route)
                                    }
                                )
                            }
                        } else {
                            cartViewModel.loadCart()
                            cartViewModel.clearSelection()
                            Toast.makeText(
                                context,
                                result.message ?: "Đặt hàng thành công",
                                Toast.LENGTH_SHORT
                            ).show()
                            navController.navigate(Screen.OrderHistory.route)
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .height(50.dp),
                enabled = canCheckout
            ) {
                if (orderViewModel.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text(checkoutLabel)
                }
            }
        }
    }
}

@Composable
fun AddressSelectionScreen(
    navController: NavController,
    orderViewModel: OrderViewModel = viewModel(),
    profileViewModel: com.example.bookstoreapp.ui.viewmodels.ProfileViewModel = viewModel()
) {
    LaunchedEffect(Unit) { profileViewModel.loadAddresses() }

    Column(modifier = Modifier.fillMaxSize()) {
        MainTopAppBar("Chọn địa chỉ giao hàng", navController)
        Column(
            modifier = Modifier
                .padding(16.dp)
                .weight(1f)
        ) {
            if (profileViewModel.addresses.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text("Chưa có địa chỉ nào", color = Color.Gray)
                }
            } else {
                profileViewModel.addresses.forEach { addr ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        onClick = {
                            orderViewModel.selectedAddress = addr
                            navController.popBackStack()
                        }
                    ) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(addr.receiverName, fontWeight = FontWeight.Bold)
                                Text(addr.addressString, color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                            }
                            androidx.compose.material3.RadioButton(
                                selected = orderViewModel.selectedAddress?.addressId == addr.addressId,
                                onClick = {
                                    orderViewModel.selectedAddress = addr
                                    navController.popBackStack()
                                }
                            )
                        }
                    }
                }
            }
        }
        Button(
            onClick = { navController.navigate(Screen.AddAddress.route) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text("+ Thêm địa chỉ mới")
        }
    }
}

@Composable
fun PaymentSelectionScreen(
    navController: NavController,
    orderViewModel: OrderViewModel = viewModel(),
    profileViewModel: com.example.bookstoreapp.ui.viewmodels.ProfileViewModel = viewModel()
) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { profileViewModel.loadPayments() }

    Column(modifier = Modifier.fillMaxSize()) {
        MainTopAppBar("Chọn phương thức thanh toán", navController)
        Column(
            modifier = Modifier
                .padding(16.dp)
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                "App hỗ trợ COD, chuyển khoản và thanh toán qua thẻ tín dụng/ghi nợ an toàn bằng Stripe.",
                color = Color.Gray,
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(12.dp))
            if (profileViewModel.payments.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text("Chưa có phương thức nào", color = Color.Gray)
                }
            } else {
                profileViewModel.payments.forEach { payment ->
                    val isSupported = payment.isSupportedInCheckout
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                            .alpha(if (isSupported) 1f else 0.6f),
                        onClick = {
                            if (isSupported) {
                                orderViewModel.selectedPayment = payment
                                navController.popBackStack()
                            } else {
                                Toast.makeText(
                                    context,
                                    "Phương thức này chưa được hỗ trợ thanh toán trong app",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    ) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(payment.paymentMethod)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    payment.displayStatus,
                                    color = if (isSupported) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    payment.checkoutHint,
                                    color = Color.Gray,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            androidx.compose.material3.RadioButton(
                                selected = orderViewModel.selectedPayment?.paymentId == payment.paymentId,
                                enabled = isSupported,
                                onClick = {
                                    if (isSupported) {
                                        orderViewModel.selectedPayment = payment
                                        navController.popBackStack()
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
        Button(
            onClick = { navController.navigate(Screen.PaymentMethod.route) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text("Quản lý thẻ & phương thức")
        }
    }
}

@Composable
private fun PaymentSupportCard(payment: PaymentItem) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                if (payment.isSupportedInCheckout) "Hướng dẫn thanh toán" else "Trạng thái phương thức",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                payment.checkoutHint,
                color = Color.Gray,
                style = MaterialTheme.typography.bodyMedium
            )

            if (payment.isBankTransfer) {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(12.dp))
                Text("Ngân hàng: Vietcombank", style = MaterialTheme.typography.bodyMedium)
                Text("Số tài khoản: 0123456789", style = MaterialTheme.typography.bodyMedium)
                Text("Chủ tài khoản: MAD N8 BookStore", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "Sau khi chuyển khoản, mở chi tiết đơn hàng và nhấn 'Xác nhận đã chuyển khoản'.",
                    color = Color.Gray,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}
