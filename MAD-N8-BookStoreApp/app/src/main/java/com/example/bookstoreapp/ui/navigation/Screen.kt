package com.example.bookstoreapp.ui.navigation

sealed class Screen(val route: String) {
    // Bottom Nav Routes
    object Home : Screen("home")
    object Profile : Screen("profile")
    object Chatbot : Screen("chatbot")
    object Cart : Screen("cart")

    // Auth Routes
    object Login : Screen("login")
    object Register : Screen("register")
    object ForgotPassword : Screen("forgot_password")
    object Otp : Screen("otp")

    // Product & Search Routes
    object ProductList : Screen("product_list")
    object ProductDetail : Screen("product_detail")
    object CategoryList : Screen("category_list")
    object Search : Screen("search")
    object ReviewList : Screen("review_list")
    
    // Cart & Checkout & Orders Routes
    object Checkout : Screen("checkout")
    object OrderHistory : Screen("order_history")
    object OrderDetail : Screen("order_detail")
    object VoucherSelection : Screen("voucher_selection")
    object ShipmentSelection : Screen("shipment_selection")
    
    // Profile sub-routes
    object EditProfile : Screen("edit_profile")
    object AddressMap : Screen("address_map")
    object AddAddress : Screen("add_address")
    object PaymentMethod : Screen("payment_method")
    object AddPaymentMethod : Screen("add_payment_method")
    object ChangePassword : Screen("change_password")
    object PaymentWebView : Screen("payment_webview")
}
