package com.example.bookstoreapp.ui.screens.product

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.bookstoreapp.ui.components.MainTopAppBar
import com.example.bookstoreapp.ui.navigation.Screen
import com.example.bookstoreapp.ui.viewmodels.BookViewModel

@Composable
fun ReviewListScreen(
    navController: NavController,
    bookId: Int,
    bookViewModel: BookViewModel = viewModel()
) {
    var showWriteDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val reviewCount = bookViewModel.bookDetail?.totalReviews ?: bookViewModel.allReviews.size

    LaunchedEffect(bookId) {
        bookViewModel.clearReviewError()
        bookViewModel.allReviews = emptyList()
        bookViewModel.loadBookDetail(bookId)
        bookViewModel.loadAllReviews(bookId)
    }

    Scaffold(
        floatingActionButton = {
            if (bookViewModel.canWriteReview) {
                FloatingActionButton(
                    onClick = {
                        bookViewModel.clearReviewError()
                        showWriteDialog = true
                    },
                    containerColor = MaterialTheme.colorScheme.primary
                ) {
                    Icon(Icons.Filled.Edit, contentDescription = "Write review", tint = Color.White)
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            MainTopAppBar("Tất cả đánh giá", navController)

            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text(
                    "$reviewCount lượt nhận xét",
                    color = Color.Gray,
                    style = MaterialTheme.typography.bodyMedium
                )

                if (!bookViewModel.canWriteReview) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Đăng nhập để viết đánh giá.",
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.bodySmall
                        )
                        TextButton(onClick = { navController.navigate(Screen.Login.route) }) {
                            Text("Đăng nhập")
                        }
                    }
                }

                bookViewModel.reviewErrorMessage?.let { error ->
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            HorizontalDivider()

            when {
                bookViewModel.isReviewLoading && bookViewModel.allReviews.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }

                bookViewModel.allReviews.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Chưa có đánh giá nào cho sản phẩm này", color = Color.Gray)
                    }
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        item { Spacer(modifier = Modifier.height(6.dp)) }
                        items(
                            items = bookViewModel.allReviews,
                            key = { it.reviewId ?: it.createdAt ?: it.comment }
                        ) { review ->
                            ReviewCard(
                                reviewerName = review.customer?.fullName ?: "Khách hàng",
                                rating = review.rating.toInt(),
                                comment = review.comment,
                                createdAt = review.createdAt.orEmpty()
                            )
                        }
                        item { Spacer(modifier = Modifier.height(80.dp)) }
                    }
                }
            }
        }
    }

    if (showWriteDialog) {
        var rating by remember { mutableStateOf(5) }
        var comment by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showWriteDialog = false },
            title = { Text("Viết đánh giá") },
            text = {
                Column {
                    Text("Chọn số sao", fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        (1..5).forEach { star ->
                            IconButton(onClick = { rating = star }) {
                                Icon(
                                    imageVector = if (star <= rating) Icons.Filled.Star else Icons.Outlined.StarOutline,
                                    contentDescription = "Select $star stars",
                                    tint = Color(0xFFC62828),
                                    modifier = Modifier.size(30.dp)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = comment,
                        onValueChange = { comment = it },
                        label = { Text("Nhận xét của bạn") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 4,
                        maxLines = 6
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        bookViewModel.postReview(bookId, rating, comment.trim()) { success ->
                            if (success) {
                                showWriteDialog = false
                                Toast.makeText(context, "Đã gửi đánh giá", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(
                                    context,
                                    bookViewModel.reviewErrorMessage ?: "Không thể gửi đánh giá",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    },
                    enabled = comment.trim().isNotEmpty() && !bookViewModel.isReviewLoading
                ) {
                    if (bookViewModel.isReviewLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Gửi đánh giá")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showWriteDialog = false }) {
                    Text("Hủy")
                }
            },
            shape = RoundedCornerShape(28.dp)
        )
    }
}

@Composable
private fun ReviewCard(
    reviewerName: String,
    rating: Int,
    comment: String,
    createdAt: String
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8F7FB)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFC62828)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            reviewerName.take(1).uppercase(),
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.size(10.dp))
                    Column {
                        Text(reviewerName, fontWeight = FontWeight.Bold)
                        if (createdAt.isNotBlank()) {
                            Text(createdAt, color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    repeat(5) { index ->
                        Icon(
                            imageVector = if (index < rating) Icons.Filled.Star else Icons.Outlined.StarOutline,
                            contentDescription = null,
                            tint = Color(0xFFC62828),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(comment)
        }
    }
}
