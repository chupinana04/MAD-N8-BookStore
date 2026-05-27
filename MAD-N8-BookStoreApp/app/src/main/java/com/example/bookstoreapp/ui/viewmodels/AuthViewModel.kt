package com.example.bookstoreapp.ui.viewmodels

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.bookstoreapp.data.api.RetrofitClient
import com.example.bookstoreapp.data.api.TokenManager
import com.example.bookstoreapp.data.local.DatabaseHelper
import com.example.bookstoreapp.data.model.*
import kotlinx.coroutines.launch

class AuthViewModel(application: Application) : AndroidViewModel(application) {
    private val api = RetrofitClient.api
    private val dbHelper = DatabaseHelper(application)

    var isLoading by mutableStateOf(false)
    var errorMessage by mutableStateOf<String?>(null)
    var loginSuccess by mutableStateOf(false)
    var registerSuccess by mutableStateOf(false)
    var otpSent by mutableStateOf(false)
    var resetToken by mutableStateOf<String?>(null)
    var passwordChanged by mutableStateOf(false)

    fun login(email: String, password: String) {
        viewModelScope.launch {
            isLoading = true; errorMessage = null
            try {
                val response = api.login(LoginRequest(email, password))
                if (response.isSuccessful && response.body() != null) {
                    val body = response.body()!!
                    TokenManager.token = body.token
                    TokenManager.customerId = body.user.customerId
                    dbHelper.setLoginData(true, body.user.email, body.token, body.user.customerId)
                    loginSuccess = true
                } else {
                    errorMessage = "Email hoặc mật khẩu không đúng"
                }
            } catch (e: Exception) {
                errorMessage = "Lỗi kết nối: ${e.message}"
            }
            isLoading = false
        }
    }

    fun register(email: String, password: String, confirmPassword: String) {
        viewModelScope.launch {
            isLoading = true; errorMessage = null
            try {
                val response = api.register(RegisterRequest(email, password, confirmPassword))
                if (response.isSuccessful) {
                    registerSuccess = true
                } else {
                    errorMessage = "Đăng ký thất bại"
                }
            } catch (e: Exception) {
                errorMessage = "Lỗi kết nối: ${e.message}"
            }
            isLoading = false
        }
    }

    fun forgotPassword(email: String) {
        viewModelScope.launch {
            isLoading = true; errorMessage = null
            try {
                val response = api.forgotPassword(ForgotPasswordRequest(email))
                if (response.isSuccessful) otpSent = true
                else errorMessage = "Email không tồn tại"
            } catch (e: Exception) {
                errorMessage = "Lỗi kết nối: ${e.message}"
            }
            isLoading = false
        }
    }

    fun verifyOtp(email: String, otpCode: String) {
        viewModelScope.launch {
            isLoading = true; errorMessage = null
            try {
                val response = api.verifyOtp(OtpRequest(email, otpCode))
                if (response.isSuccessful && response.body() != null) {
                    resetToken = response.body()!!.resetToken
                } else {
                    errorMessage = "Mã OTP không đúng"
                }
            } catch (e: Exception) {
                errorMessage = "Lỗi kết nối: ${e.message}"
            }
            isLoading = false
        }
    }

    fun changePassword(oldPassword: String?, newPassword: String, confirmPassword: String, resetTokenVal: String? = null) {
        viewModelScope.launch {
            isLoading = true; errorMessage = null
            try {
                val request = ChangePasswordRequest(oldPassword, newPassword, confirmPassword, resetTokenVal)
                val response = api.changePassword(request)
                if (response.isSuccessful) passwordChanged = true
                else errorMessage = "Đổi mật khẩu thất bại"
            } catch (e: Exception) {
                errorMessage = "Lỗi kết nối: ${e.message}"
            }
            isLoading = false
        }
    }

    fun restoreSession() {
        val token = dbHelper.getToken()
        val cid = dbHelper.getCustomerId()
        if (!token.isNullOrEmpty() && cid > 0) {
            TokenManager.token = token
            TokenManager.customerId = cid
        }
    }
}
