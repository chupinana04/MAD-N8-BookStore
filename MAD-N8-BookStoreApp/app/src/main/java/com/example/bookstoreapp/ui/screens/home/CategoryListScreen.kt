package com.example.bookstoreapp.ui.screens.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.bookstoreapp.ui.components.MainTopAppBar
import com.example.bookstoreapp.ui.viewmodels.BookViewModel

@Composable
fun CategoryListScreen(navController: NavController, bookViewModel: BookViewModel = viewModel()) {
    LaunchedEffect(Unit) { bookViewModel.loadCategories() }

    Column(modifier = Modifier.fillMaxSize()) {
        MainTopAppBar(title = "Tất cả danh mục", navController = navController)
        LazyColumn(modifier = Modifier.padding(16.dp)) {
            items(bookViewModel.categories) { category ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    onClick = { navController.navigate("product_list?categoryId=${category.categoryId}&title=${category.categoryName}") }
                ) {
                    Text(text = category.categoryName, modifier = Modifier.padding(16.dp))
                }
            }
        }
    }
}
