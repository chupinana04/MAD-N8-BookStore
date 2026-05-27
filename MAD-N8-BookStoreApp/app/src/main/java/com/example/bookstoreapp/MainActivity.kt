package com.example.bookstoreapp

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import com.example.bookstoreapp.ui.theme.BookStoreAppTheme

import androidx.navigation.compose.rememberNavController
import com.example.bookstoreapp.data.api.TokenManager
import com.example.bookstoreapp.data.local.DatabaseHelper
import com.example.bookstoreapp.ui.navigation.AppNavigation
import com.example.bookstoreapp.ui.navigation.BottomNavBar
import com.stripe.android.PaymentConfiguration

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

        // Khởi tạo Stripe
        PaymentConfiguration.init(
            applicationContext,
            "pk_test_51TW4UZI7gYbiJ6bgVfo4HclEQHAp05nIZDkT9pgM9onvMkevKXpWVwY3uaftcRlMIwnO3ksfqf67cmZNkBfLIqsI00mvwdb6Wd"
        )

        // Khôi phục phiên đăng nhập từ SQLite
        val dbHelper = DatabaseHelper(this)
        val token = dbHelper.getToken()
        val customerId = dbHelper.getCustomerId()
        if (!token.isNullOrEmpty() && customerId > 0) {
            TokenManager.token = token
            TokenManager.customerId = customerId
        }

        enableEdgeToEdge()
        setContent {
            BookStoreAppTheme {
                val navController = rememberNavController()
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    bottomBar = { BottomNavBar(navController = navController) }
                ) { innerPadding ->
                    AppNavigation(
                        navController = navController,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}
