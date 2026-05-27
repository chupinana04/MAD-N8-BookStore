package com.example.bookstoreapp.ui.screens.cart

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LocalShipping
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
fun ShipmentSelectionScreen(navController: NavController, orderViewModel: OrderViewModel = viewModel()) {
    LaunchedEffect(Unit) { orderViewModel.loadShipments() }

    Column(modifier = Modifier.fillMaxSize()) {
        MainTopAppBar("Chọn đơn vị vận chuyển", navController)

        Column(modifier = Modifier.padding(16.dp)) {
            if (orderViewModel.shipments.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else {
                orderViewModel.shipments.forEach { shipment ->
                    Card(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp), onClick = {
                        orderViewModel.selectedShipment = shipment
                        navController.popBackStack()
                    }) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.LocalShipping, contentDescription = null, tint = Color(0xFF1976D2), modifier = Modifier.size(32.dp))
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(shipment.shipmentMethod, fontWeight = FontWeight.Bold)
                                if (shipment.estimatedDate != null) Text("Dự kiến: ${shipment.estimatedDate}", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                            }
                            RadioButton(selected = orderViewModel.selectedShipment?.shipmentId == shipment.shipmentId, onClick = {
                                orderViewModel.selectedShipment = shipment
                                navController.popBackStack()
                            })
                        }
                    }
                }
            }
        }
    }
}
