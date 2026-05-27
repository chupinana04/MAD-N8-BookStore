package com.example.bookstoreapp.ui.screens.cart

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.bookstoreapp.ui.components.MainTopAppBar
import com.example.bookstoreapp.ui.viewmodels.OrderViewModel

@Composable
fun VoucherSelectionScreen(navController: NavController, totalAmount: Double, orderViewModel: OrderViewModel = viewModel()) {
    var inputCode by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { orderViewModel.loadVouchers() }

    Column(modifier = Modifier.fillMaxSize()) {
        MainTopAppBar("Áp dụng mã giảm giá", navController)

        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(value = inputCode, onValueChange = { inputCode = it }, placeholder = { Text("Nhập mã Voucher") }, modifier = Modifier.weight(1f))
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = {
                    orderViewModel.validateVoucher(inputCode, totalAmount) // Validate
                }) { Text("Áp dụng") }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text("Voucher khả dụng", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn(modifier = Modifier.weight(1f)) {
                items(orderViewModel.vouchers) { voucher ->
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp).clickable {
                            orderViewModel.selectedVoucher = voucher
                            navController.popBackStack()
                        },
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F8E9))
                    ) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(voucher.description ?: voucher.code, fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                                Text("Mã: ${voucher.code}", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                                if (voucher.minOrderValue != null) {
                                    val formattedMin = java.text.NumberFormat.getCurrencyInstance(java.util.Locale("vi", "VN")).format(voucher.minOrderValue * 100000)
                                    Text("Đơn tối thiểu: $formattedMin", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                                }
                                if (voucher.expiryDate != null) Text("HSD: ${voucher.expiryDate}", color = Color.Gray, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
                            }
                            RadioButton(selected = orderViewModel.selectedVoucher?.voucherId == voucher.voucherId, onClick = {
                                orderViewModel.selectedVoucher = voucher
                                navController.popBackStack()
                            })
                        }
                    }
                }
            }
        }
    }
}
