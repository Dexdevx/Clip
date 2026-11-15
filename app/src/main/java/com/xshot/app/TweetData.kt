package com.xshot.app

data class TweetData(
    val id: String,
    val text: String,
    val authorName: String,
    val authorHandle: String,
    val avatarUrl: String?,
    val isVerified: Boolean = false,
    val mediaUrls: List<String> = emptyList()
)
