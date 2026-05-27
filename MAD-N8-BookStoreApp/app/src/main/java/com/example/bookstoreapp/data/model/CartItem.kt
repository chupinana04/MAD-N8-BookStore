package com.example.bookstoreapp.data.model

data class CartItem(
    val id: String,
    val name: String,
    val price: Double,
    val imageUrl: String = "",
    var quantity: Int = 1,
    var isSelected: Boolean = false
)
