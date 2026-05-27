package com.example.bookstoreapp.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.example.bookstoreapp.data.model.Book
import com.example.bookstoreapp.ui.navigation.Screen
import com.example.bookstoreapp.ui.viewmodels.BookViewModel

@Composable
fun HomeScreen(navController: NavController, bookViewModel: BookViewModel = viewModel()) {
    LaunchedEffect(Unit) {
        bookViewModel.loadCategories()
        bookViewModel.loadForYou()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .clickable { navController.navigate(Screen.Search.route) },
            shadowElevation = 4.dp,
            shape = RoundedCornerShape(8.dp)
        ) {
            Text(
                "Tìm kiếm sản phẩm...",
                modifier = Modifier.padding(16.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Danh mục", style = MaterialTheme.typography.titleLarge)
            Text(
                "Xem tất cả",
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable { navController.navigate(Screen.CategoryList.route) }
            )
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(bookViewModel.categories) { category ->
                Card(
                    modifier = Modifier.clickable {
                        navController.navigate("product_list?categoryId=${category.categoryId}&title=${category.categoryName}")
                    }
                ) {
                    Text(category.categoryName, modifier = Modifier.padding(16.dp))
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "Dành riêng cho bạn",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(bookViewModel.forYouBooks) { book ->
                BookGridCard(book = book) {
                    navController.navigate("product_detail/${book.bookId}")
                }
            }
        }
    }
}

@Composable
internal fun BookGridCard(book: Book, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(316.dp)
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 8.dp, vertical = 6.dp)
                .fillMaxSize()
        ) {
            if (book.primaryImageUrl.isNotEmpty()) {
                AsyncImage(
                    model = book.primaryImageUrl,
                    contentDescription = book.title,
                    modifier = Modifier
                        .height(176.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp)),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .height(176.dp)
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
            Text(book.title, style = MaterialTheme.typography.titleMedium, maxLines = 2)
            if (!book.author.isNullOrEmpty()) {
                Text(
                    book.author,
                    color = Color.Gray,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "★ ${String.format("%.1f", book.avgRating ?: 0.0)}",
                    color = Color(0xFFFF9800),
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    "Đã bán ${book.soldCount ?: 0}",
                    color = Color.Gray,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Spacer(modifier = Modifier.weight(1f))
            Text(
                "${"%,.0f".format(book.price)}đ",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}
