package com.example.bookstoreapp.ui.screens.auth

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.bookstoreapp.ui.navigation.Screen
import com.example.bookstoreapp.ui.viewmodels.AuthViewModel

@Composable
fun LoginScreen(navController: NavController, authViewModel: AuthViewModel = viewModel()) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val context = LocalContext.current

    LaunchedEffect(authViewModel.loginSuccess) {
        if (authViewModel.loginSuccess) {
            authViewModel.loginSuccess = false
            navController.navigate(Screen.Home.route) { popUpTo(0) }
        }
    }
    LaunchedEffect(authViewModel.errorMessage) {
        authViewModel.errorMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            authViewModel.errorMessage = null
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text("Đăng nhập", style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Email") }, modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(value = password, onValueChange = { password = it }, label = { Text("Mật khẩu") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = { authViewModel.login(email, password) },
            modifier = Modifier.fillMaxWidth(),
            enabled = !authViewModel.isLoading
        ) {
            if (authViewModel.isLoading) CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary)
            else Text("Đăng nhập")
        }
        TextButton(onClick = { navController.navigate(Screen.Register.route) }) { Text("Chưa có tài khoản? Đăng ký ngay") }
        TextButton(onClick = { navController.navigate(Screen.ForgotPassword.route) }) { Text("Quên mật khẩu?") }
    }
}

@Composable
fun RegisterScreen(navController: NavController, authViewModel: AuthViewModel = viewModel()) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    val context = LocalContext.current

    LaunchedEffect(authViewModel.registerSuccess) {
        if (authViewModel.registerSuccess) {
            authViewModel.registerSuccess = false
            Toast.makeText(context, "Đăng ký thành công! Nhập OTP.", Toast.LENGTH_SHORT).show()
            navController.navigate(Screen.Otp.route)
        }
    }
    LaunchedEffect(authViewModel.errorMessage) {
        authViewModel.errorMessage?.let { Toast.makeText(context, it, Toast.LENGTH_SHORT).show(); authViewModel.errorMessage = null }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text("Đăng ký", style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Email") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = password, onValueChange = { password = it }, label = { Text("Mật khẩu") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = confirmPassword, onValueChange = { confirmPassword = it }, label = { Text("Xác nhận mật khẩu") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = { authViewModel.register(email, password, confirmPassword) }, modifier = Modifier.fillMaxWidth(), enabled = !authViewModel.isLoading) {
            if (authViewModel.isLoading) CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary)
            else Text("Đăng ký")
        }
    }
}

@Composable
fun ForgotPasswordScreen(navController: NavController, authViewModel: AuthViewModel = viewModel()) {
    var email by remember { mutableStateOf("") }
    val context = LocalContext.current

    LaunchedEffect(authViewModel.otpSent) {
        if (authViewModel.otpSent) {
            authViewModel.otpSent = false
            Toast.makeText(context, "OTP đã gửi!", Toast.LENGTH_SHORT).show()
            navController.navigate(Screen.Otp.route)
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text("Quên mật khẩu", style = MaterialTheme.typography.headlineMedium)
        OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Nhập Email") }, modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = { authViewModel.forgotPassword(email) }, modifier = Modifier.fillMaxWidth(), enabled = !authViewModel.isLoading) { Text("Gửi OTP") }
    }
}

@Composable
fun OtpScreen(navController: NavController, authViewModel: AuthViewModel = viewModel()) {
    var email by remember { mutableStateOf("") }
    var otp by remember { mutableStateOf("") }
    val context = LocalContext.current

    LaunchedEffect(authViewModel.resetToken) {
        authViewModel.resetToken?.let {
            Toast.makeText(context, "Xác minh thành công!", Toast.LENGTH_SHORT).show()
            navController.navigate(Screen.Login.route)
        }
    }
    LaunchedEffect(authViewModel.errorMessage) {
        authViewModel.errorMessage?.let { Toast.makeText(context, it, Toast.LENGTH_SHORT).show(); authViewModel.errorMessage = null }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text("Xác minh OTP", style = MaterialTheme.typography.headlineMedium)
        OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Email") }, modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(value = otp, onValueChange = { otp = it }, label = { Text("Nhập mã OTP") }, modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = { authViewModel.verifyOtp(email, otp) }, modifier = Modifier.fillMaxWidth(), enabled = !authViewModel.isLoading) { Text("Xác nhận") }
    }
}
