package com.example.bookstoreapp.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import java.net.URLDecoder
import java.net.URLEncoder
import com.example.bookstoreapp.ui.screens.auth.ForgotPasswordScreen
import com.example.bookstoreapp.ui.screens.auth.LoginScreen
import com.example.bookstoreapp.ui.screens.auth.OtpScreen
import com.example.bookstoreapp.ui.screens.auth.RegisterScreen
import com.example.bookstoreapp.ui.screens.cart.AddressSelectionScreen
import com.example.bookstoreapp.ui.screens.cart.CartScreen
import com.example.bookstoreapp.ui.screens.cart.CheckoutScreen
import com.example.bookstoreapp.ui.screens.cart.PaymentSelectionScreen
import com.example.bookstoreapp.ui.screens.cart.PaymentWebViewScreen
import com.example.bookstoreapp.ui.screens.cart.ShipmentSelectionScreen
import com.example.bookstoreapp.ui.screens.cart.VoucherSelectionScreen
import com.example.bookstoreapp.ui.screens.chatbot.ChatbotScreen
import com.example.bookstoreapp.ui.screens.home.CategoryListScreen
import com.example.bookstoreapp.ui.screens.home.HomeScreen
import com.example.bookstoreapp.ui.screens.orders.OrderDetailScreen
import com.example.bookstoreapp.ui.screens.orders.OrderHistoryScreen
import com.example.bookstoreapp.ui.screens.product.ProductDetailScreen
import com.example.bookstoreapp.ui.screens.product.ProductListScreen
import com.example.bookstoreapp.ui.screens.product.ReviewListScreen
import com.example.bookstoreapp.ui.screens.profile.AddAddressScreen
import com.example.bookstoreapp.ui.screens.profile.AddressScreen
import com.example.bookstoreapp.ui.screens.profile.ChangePasswordScreen
import com.example.bookstoreapp.ui.screens.profile.EditProfileScreen
import com.example.bookstoreapp.ui.screens.profile.PaymentMethodScreen
import com.example.bookstoreapp.ui.screens.profile.ProfileScreen
import com.example.bookstoreapp.ui.screens.search.ImageSearchScreen
import com.example.bookstoreapp.ui.screens.search.SearchScreen
import com.example.bookstoreapp.ui.screens.search.AuthorBooksScreen
import com.example.bookstoreapp.ui.screens.search.AuthorListScreen
import com.example.bookstoreapp.ui.viewmodels.BookViewModel
import com.example.bookstoreapp.ui.viewmodels.CartViewModel
import com.example.bookstoreapp.ui.viewmodels.OrderViewModel
import com.example.bookstoreapp.ui.viewmodels.ProfileViewModel

private const val NAV_ANIMATION_DURATION = 280
private val BOTTOM_BAR_ROUTES = setOf(
    Screen.Home.route,
    Screen.Cart.route,
    Screen.Chatbot.route,
    Screen.Profile.route
)
private val SEARCH_CONTEXT_ROUTES = setOf(
    Screen.Search.route,
    "image_search",
    "author_list",
    "author_books/{authorId}",
    "product_detail/{bookId}",
    "review_list/{bookId}"
)

@Composable
fun AppNavigation(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    val cartViewModel: CartViewModel = viewModel()
    val bookViewModel: BookViewModel = viewModel()
    val orderViewModel: OrderViewModel = viewModel()
    val profileViewModel: ProfileViewModel = viewModel()
    val currentBackStackEntry = navController.currentBackStackEntryAsState().value
    val currentRoute = currentBackStackEntry?.destination?.route

    LaunchedEffect(currentRoute) {
        if (currentRoute != null && currentRoute !in SEARCH_CONTEXT_ROUTES) {
            bookViewModel.clearSearch()
        }
    }

    NavHost(
        navController = navController,
        startDestination = Screen.Home.route,
        modifier = modifier,
        enterTransition = {
            if (initialState.destination.route in BOTTOM_BAR_ROUTES && targetState.destination.route in BOTTOM_BAR_ROUTES) {
                fadeIn(animationSpec = tween(NAV_ANIMATION_DURATION))
            } else {
                slideIntoContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Left,
                    animationSpec = tween(NAV_ANIMATION_DURATION)
                )
            }
        },
        exitTransition = {
            if (initialState.destination.route in BOTTOM_BAR_ROUTES && targetState.destination.route in BOTTOM_BAR_ROUTES) {
                fadeOut(animationSpec = tween(NAV_ANIMATION_DURATION))
            } else {
                slideOutOfContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Left,
                    animationSpec = tween(NAV_ANIMATION_DURATION)
                )
            }
        },
        popEnterTransition = {
            if (initialState.destination.route in BOTTOM_BAR_ROUTES && targetState.destination.route in BOTTOM_BAR_ROUTES) {
                fadeIn(animationSpec = tween(NAV_ANIMATION_DURATION))
            } else {
                slideIntoContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Right,
                    animationSpec = tween(NAV_ANIMATION_DURATION)
                )
            }
        },
        popExitTransition = {
            if (initialState.destination.route in BOTTOM_BAR_ROUTES && targetState.destination.route in BOTTOM_BAR_ROUTES) {
                fadeOut(animationSpec = tween(NAV_ANIMATION_DURATION))
            } else {
                slideOutOfContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Right,
                    animationSpec = tween(NAV_ANIMATION_DURATION)
                )
            }
        }
    ) {
        composable(Screen.Login.route) { LoginScreen(navController) }
        composable(Screen.Register.route) { RegisterScreen(navController) }
        composable(Screen.ForgotPassword.route) { ForgotPasswordScreen(navController) }
        composable(Screen.Otp.route) { OtpScreen(navController) }

        composable(Screen.Home.route) { HomeScreen(navController, bookViewModel) }
        composable(Screen.Profile.route) { ProfileScreen(navController) }
        composable(Screen.Chatbot.route) { ChatbotScreen(navController) }
        composable(Screen.Cart.route) { CartScreen(navController, cartViewModel) }

        composable(Screen.EditProfile.route) { EditProfileScreen(navController, profileViewModel) }
        composable(Screen.AddressMap.route) { AddressScreen(navController, profileViewModel) }
        composable(Screen.AddAddress.route) { AddAddressScreen(navController, profileViewModel) }
        composable(Screen.PaymentMethod.route) { PaymentMethodScreen(navController, profileViewModel) }
        composable(Screen.ChangePassword.route) { ChangePasswordScreen(navController) }

        composable(Screen.ProductList.route) { ProductListScreen(navController, bookViewModel = bookViewModel) }
        composable(
            "product_list?categoryId={categoryId}&authorId={authorId}&title={title}",
            arguments = listOf(
                navArgument("categoryId") { type = NavType.IntType; defaultValue = -1 },
                navArgument("authorId") { type = NavType.IntType; defaultValue = -1 },
                navArgument("title") { type = NavType.StringType; defaultValue = "Tất cả sản phẩm" }
            )
        ) { backStackEntry ->
            val catId = backStackEntry.arguments?.getInt("categoryId")?.let { if (it == -1) null else it }
            val authId = backStackEntry.arguments?.getInt("authorId")?.let { if (it == -1) null else it }
            val title = backStackEntry.arguments?.getString("title") ?: "Tất cả sản phẩm"
            ProductListScreen(navController, categoryId = catId, authorId = authId, title = title, bookViewModel = bookViewModel)
        }

        composable(
            "product_detail/{bookId}",
            arguments = listOf(navArgument("bookId") { type = NavType.IntType })
        ) { backStackEntry ->
            val bookId = backStackEntry.arguments?.getInt("bookId") ?: 1
            ProductDetailScreen(navController, bookId, cartViewModel, bookViewModel)
        }

        composable(Screen.CategoryList.route) { CategoryListScreen(navController, bookViewModel) }
        composable(Screen.Search.route) { SearchScreen(navController, bookViewModel) }
        composable("image_search") { ImageSearchScreen(navController, bookViewModel) }
        composable("author_list") { AuthorListScreen(navController, bookViewModel) }
        composable(
            "author_books/{authorId}",
            arguments = listOf(navArgument("authorId") { type = NavType.IntType })
        ) { backStackEntry ->
            val authorId = backStackEntry.arguments?.getInt("authorId") ?: 0
            AuthorBooksScreen(navController, authorId, bookViewModel)
        }

        composable(
            "review_list/{bookId}",
            arguments = listOf(navArgument("bookId") { type = NavType.IntType })
        ) { backStackEntry ->
            val bookId = backStackEntry.arguments?.getInt("bookId") ?: 1
            ReviewListScreen(navController, bookId, bookViewModel)
        }

        composable(Screen.Checkout.route) { CheckoutScreen(navController, orderViewModel, cartViewModel) }
        composable(Screen.OrderHistory.route) { OrderHistoryScreen(navController, orderViewModel) }

        composable(
            "order_detail/{orderId}",
            arguments = listOf(navArgument("orderId") { type = NavType.IntType })
        ) { backStackEntry ->
            val orderId = backStackEntry.arguments?.getInt("orderId") ?: 0
            OrderDetailScreen(navController, orderId, orderViewModel)
        }

        composable(
            "voucher_selection/{totalAmount}",
            arguments = listOf(navArgument("totalAmount") { type = NavType.FloatType })
        ) { backStackEntry ->
            val totalAmount = backStackEntry.arguments?.getFloat("totalAmount")?.toDouble() ?: 0.0
            VoucherSelectionScreen(navController, totalAmount, orderViewModel)
        }
        composable(Screen.ShipmentSelection.route) { ShipmentSelectionScreen(navController, orderViewModel) }
        composable("address_selection") { AddressSelectionScreen(navController, orderViewModel, profileViewModel) }
        composable("payment_selection") { PaymentSelectionScreen(navController, orderViewModel, profileViewModel) }
        
        composable(
            "payment_webview/{url}",
            arguments = listOf(navArgument("url") { type = NavType.StringType })
        ) { backStackEntry ->
            val encodedUrl = backStackEntry.arguments?.getString("url") ?: ""
            val url = URLDecoder.decode(encodedUrl, "UTF-8")
            PaymentWebViewScreen(navController, url)
        }
    }
}
