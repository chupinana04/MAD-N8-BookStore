package com.example.bookstoreapp.ui.viewmodels

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.bookstoreapp.data.api.RetrofitClient
import com.example.bookstoreapp.data.api.TokenManager
import com.example.bookstoreapp.data.model.*
import kotlinx.coroutines.launch

class CartViewModel : ViewModel() {
    private val api = RetrofitClient.api

    var cartItems = mutableStateListOf<CartItemResponse>()
    var cartId by mutableStateOf(-1)
    var totalAmount by mutableStateOf(0.0)
    var isLoading by mutableStateOf(false)

    // Local selection state (not synced to backend)
    private val selectedIds = mutableStateListOf<Int>()

    fun loadCart() {
        if (TokenManager.customerId <= 0) return
        viewModelScope.launch {
            isLoading = true
            try {
                val response = api.getCart(TokenManager.customerId)
                if (response.isSuccessful && response.body() != null) {
                    val body = response.body()!!
                    cartId = body.cartId
                    cartItems.clear()
                    cartItems.addAll(body.items)
                    totalAmount = body.totalAmount
                    val validIds = body.items.map { it.cartItemId }.toSet()
                    selectedIds.removeAll { it !in validIds }
                }
            } catch (_: Exception) {}
            isLoading = false
        }
    }

    fun addToCart(bookId: Int, quantity: Int = 1, onDone: (Boolean, String?) -> Unit = { _, _ -> }) {
        if (TokenManager.customerId <= 0) {
            onDone(false, "Vui lòng đăng nhập để thêm vào giỏ hàng")
            return
        }
        viewModelScope.launch {
            try {
                val response = api.addToCart(AddToCartRequest(TokenManager.customerId, bookId, quantity))
                if (response.isSuccessful) {
                    loadCart()
                    onDone(true, response.body()?.message ?: "Đã thêm vào giỏ hàng")
                } else {
                    onDone(false, "Không thể thêm sản phẩm vào giỏ hàng")
                }
            } catch (e: Exception) {
                onDone(false, e.message ?: "Không thể thêm sản phẩm vào giỏ hàng")
            }
        }
    }

    fun updateQuantity(cartItemId: Int, newQuantity: Int) {
        if (newQuantity < 1) return
        viewModelScope.launch {
            try {
                api.updateCartItem(cartItemId, UpdateQuantityRequest(newQuantity))
                loadCart()
            } catch (_: Exception) {}
        }
    }

    fun deleteItem(cartItemId: Int) {
        viewModelScope.launch {
            try {
                api.deleteCartItem(cartItemId)
                selectedIds.remove(cartItemId)
                loadCart()
            } catch (_: Exception) {}
        }
    }

    fun toggleSelection(cartItemId: Int, isSelected: Boolean) {
        if (isSelected) { if (!selectedIds.contains(cartItemId)) selectedIds.add(cartItemId) }
        else selectedIds.remove(cartItemId)
    }

    fun isSelected(cartItemId: Int): Boolean = selectedIds.contains(cartItemId)

    val selectedCartItemIds: List<Int>
        get() = selectedIds.filter { selectedId -> cartItems.any { it.cartItemId == selectedId } }

    val selectedTotalPrice: Double
        get() = cartItems.filter { selectedIds.contains(it.cartItemId) }
            .sumOf { (it.bookPrice ?: 0.0) * it.quantity }

    val hasSelectedItems: Boolean
        get() = selectedIds.any { id -> cartItems.any { it.cartItemId == id } }

    fun clearSelection() {
        selectedIds.clear()
    }
}
