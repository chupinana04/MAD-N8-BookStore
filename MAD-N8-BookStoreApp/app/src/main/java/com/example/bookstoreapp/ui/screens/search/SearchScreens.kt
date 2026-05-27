package com.example.bookstoreapp.ui.screens.search

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items as lazyItems
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.bookstoreapp.ui.components.MainTopAppBar
import com.example.bookstoreapp.ui.screens.home.BookGridCard
import com.example.bookstoreapp.ui.viewmodels.BookViewModel
import kotlinx.coroutines.delay

@Composable
fun SearchScreen(navController: NavController, bookViewModel: BookViewModel = viewModel()) {
    val query = bookViewModel.searchQuery
    var showVoiceDialog by remember { mutableStateOf(false) }
    val hasSearched = bookViewModel.isSearchActive

    LaunchedEffect(query, bookViewModel.recognizedText) {
        val normalizedQuery = query.trim()
        if (normalizedQuery.length >= 2 && normalizedQuery != bookViewModel.recognizedText?.trim()) {
            delay(350)
            if (normalizedQuery == bookViewModel.searchQuery.trim()) {
                bookViewModel.search(normalizedQuery)
            }
        } else if (normalizedQuery.isEmpty() && hasSearched) {
            bookViewModel.clearSearch(resetQuery = false)
        }
    }

    if (showVoiceDialog) {
        VoiceSearchDialog(
            onDismiss = { showVoiceDialog = false },
            bookViewModel = bookViewModel,
            onResult = { }
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        MainTopAppBar("Tìm kiếm", navController)

        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { bookViewModel.updateSearchQuery(it) },
                    placeholder = { Text("Tìm sách, tác giả hoặc thể loại") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                IconButton(onClick = { showVoiceDialog = true }) {
                    Icon(Icons.Filled.Mic, contentDescription = "Tìm bằng giọng nói", tint = MaterialTheme.colorScheme.primary)
                }
                IconButton(onClick = { navController.navigate("image_search") }) {
                    Icon(Icons.Filled.CameraAlt, contentDescription = "Tìm bằng hình ảnh", tint = MaterialTheme.colorScheme.primary)
                }
            }
        }

        when {
            bookViewModel.isLoading -> {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            hasSearched && bookViewModel.searchResults.isEmpty() && bookViewModel.searchAuthorMatches.isEmpty() -> {
                SearchEmptyState("Không tìm thấy kết quả phù hợp")
            }

            hasSearched || bookViewModel.recognizedText != null -> {
                Column(modifier = Modifier.weight(1f)) {
                    if (bookViewModel.searchAuthorMatches.isNotEmpty()) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                                .clickable { navController.navigate("author_list") },
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                        ) {
                            Row(
                                modifier = Modifier
                                    .padding(16.dp)
                                    .fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        "Tác giả phù hợp",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        "${bookViewModel.searchAuthorMatches.size} kết quả",
                                        color = Color.Gray,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                Icon(Icons.Filled.KeyboardArrowRight, contentDescription = "Xem tác giả")
                            }
                        }
                    }

                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        items(bookViewModel.searchResults) { book ->
                            BookGridCard(book = book) {
                                navController.navigate("product_detail/${book.bookId}")
                            }
                        }
                    }
                }
            }

            else -> {
                SearchEmptyState("Nhập từ khóa để bắt đầu tìm kiếm")
            }
        }
    }
}

@Composable
fun AuthorListScreen(navController: NavController, bookViewModel: BookViewModel = viewModel()) {
    Column(modifier = Modifier.fillMaxSize()) {
        MainTopAppBar("Tác giả tìm kiếm", navController)

        if (bookViewModel.searchAuthorMatches.isEmpty()) {
            SearchEmptyState("Không có tác giả phù hợp")
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                lazyItems(bookViewModel.searchAuthorMatches) { author ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                bookViewModel.selectedAuthor = author
                                navController.navigate("author_books/${author.authorID}")
                            },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(16.dp)
                                .fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    author.fullName ?: "Không rõ",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                if (!author.bio.isNullOrBlank()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        author.bio,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.Gray,
                                        maxLines = 2
                                    )
                                }
                            }
                            Text("Xem", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AuthorBooksScreen(navController: NavController, authorId: Int, bookViewModel: BookViewModel = viewModel()) {
    val author = bookViewModel.selectedAuthor

    LaunchedEffect(authorId) {
        bookViewModel.loadBooksByAuthor(authorId)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        MainTopAppBar(author?.fullName ?: "Tác giả", navController)

        if (author != null) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        author.fullName ?: "",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    if (!author.bio.isNullOrEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(author.bio, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }

        if (bookViewModel.isLoading) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (bookViewModel.bookList.isEmpty()) {
            SearchEmptyState("Tác giả này chưa có sách hiển thị")
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(bookViewModel.bookList) { book ->
                    BookGridCard(book = book) {
                        navController.navigate("product_detail/${book.bookId}")
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchEmptyState(message: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
