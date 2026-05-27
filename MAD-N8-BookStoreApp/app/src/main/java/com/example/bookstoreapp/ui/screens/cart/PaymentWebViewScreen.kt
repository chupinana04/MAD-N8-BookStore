package com.example.bookstoreapp.ui.screens.cart

import android.annotation.SuppressLint
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavController
import com.example.bookstoreapp.ui.components.MainTopAppBar
import com.example.bookstoreapp.ui.navigation.Screen

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun PaymentWebViewScreen(navController: NavController, url: String) {
    Scaffold(
        topBar = { MainTopAppBar("Thanh toán PayOS", navController) }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            AndroidView(
                factory = { context ->
                    WebView(context).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.loadWithOverviewMode = true
                        settings.useWideViewPort = true
                        settings.userAgentString = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Mobile Safari/537.36"
                        
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(
                                view: WebView?,
                                request: WebResourceRequest?
                            ): Boolean {
                                val currentUrl = request?.url?.toString() ?: ""
                                
                                // Kiểm tra URL thành công hoặc hủy từ backend
                                if (currentUrl.contains("/api/payos/success")) {
                                    navController.navigate(Screen.OrderHistory.route) {
                                        popUpTo(Screen.Cart.route) { inclusive = false }
                                    }
                                    return true
                                }
                                if (currentUrl.contains("/api/payos/cancel")) {
                                    navController.popBackStack()
                                    return true
                                }
                                return false
                            }
                        }
                        loadUrl(url)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
