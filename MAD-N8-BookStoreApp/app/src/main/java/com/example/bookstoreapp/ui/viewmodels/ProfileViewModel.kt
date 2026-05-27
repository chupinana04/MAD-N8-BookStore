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

class ProfileViewModel : ViewModel() {
    private val api = RetrofitClient.api

    var profile by mutableStateOf<ProfileResponse?>(null)
    var addresses by mutableStateOf<List<AddressItem>>(emptyList())
    var payments by mutableStateOf<List<PaymentItem>>(emptyList())
    var message by mutableStateOf<String?>(null)

    fun loadProfile() {
        viewModelScope.launch {
            try {
                val response = api.getProfile(TokenManager.customerId)
                if (response.isSuccessful) profile = response.body()
            } catch (_: Exception) {}
        }
    }

    fun updateProfile(fullName: String, phoneNumber: String, onDone: () -> Unit) {
        viewModelScope.launch {
            try {
                val response = api.updateProfile(UpdateProfileRequest(TokenManager.customerId, fullName, phoneNumber))
                if (response.isSuccessful) { loadProfile(); onDone() }
                else message = "Cập nhật thất bại"
            } catch (e: Exception) { message = e.message }
        }
    }

    fun loadAddresses() {
        viewModelScope.launch {
            try {
                val response = api.getAddresses(TokenManager.customerId)
                if (response.isSuccessful) addresses = response.body() ?: emptyList()
            } catch (_: Exception) {}
        }
    }

    fun createAddress(name: String, addressStr: String, onDone: () -> Unit) {
        viewModelScope.launch {
            try {
                val response = api.createAddress(CreateAddressRequest(TokenManager.customerId, name, addressStr))
                if (response.isSuccessful) { loadAddresses(); onDone() }
            } catch (_: Exception) {}
        }
    }

    fun deleteAddress(id: Int) {
        viewModelScope.launch {
            try { api.deleteAddress(id); loadAddresses() } catch (_: Exception) {}
        }
    }

    fun loadPayments() {
        viewModelScope.launch {
            try {
                val response = api.getPayments(TokenManager.customerId)
                if (response.isSuccessful) payments = response.body() ?: emptyList()
            } catch (_: Exception) {}
        }
    }

    fun createPayment(method: String, onDone: () -> Unit) {
        viewModelScope.launch {
            try {
                val response = api.createPayment(CreatePaymentRequest(TokenManager.customerId, method))
                if (response.isSuccessful) { loadPayments(); onDone() }
            } catch (_: Exception) {}
        }
    }

    fun deletePayment(id: Int) {
        viewModelScope.launch {
            try { api.deletePayment(id); loadPayments() } catch (_: Exception) {}
        }
    }

    fun createSetupIntent(onSuccess: (String) -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val response = api.createSetupIntent(CreateSetupIntentRequest(TokenManager.customerId))
                if (response.isSuccessful && response.body() != null) {
                    onSuccess(response.body()!!.clientSecret)
                } else {
                    onError("Không thể tạo kết nối Stripe")
                }
            } catch (e: Exception) {
                onError(e.message ?: "Lỗi kết nối Stripe")
            }
        }
    }

    fun saveStripeCard(onDone: () -> Unit) {
        viewModelScope.launch {
            try {
                val response = api.saveStripeCard(SaveStripeCardRequest(TokenManager.customerId))
                if (response.isSuccessful) {
                    loadPayments()
                }
            } catch (_: Exception) {}
            onDone()
        }
    }
}
