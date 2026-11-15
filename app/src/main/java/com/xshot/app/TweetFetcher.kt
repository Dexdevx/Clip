package com.xshot.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

object TweetFetcher {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    fun extractTweetId(url: String): String? {
        val pattern = Pattern.compile("(?:twitter\\.com|x\\.com|fxtwitter\\.com|vxtwitter\\.com)/.*?/status/(\\d+)")
        val matcher = pattern.matcher(url)
        return if (matcher.find()) {
            matcher.group(1)
        } else if (url.trim().matches(Regex("^\\d+$"))) {
            url.trim()
        } else {
            null
        }
    }

    suspend fun fetch(urlOrId: String): TweetData = withContext(Dispatchers.IO) {
        val id = extractTweetId(urlOrId) ?: throw IllegalArgumentException("Invalid X / Twitter link")
        
        // 1. Try FixTweet API (fast, clean, contains media details)
        try {
            val fxUrl = "https://api.fxtwitter.com/status/$id"
            val request = Request.Builder()
                .url(fxUrl)
                .header("User-Agent", "XShot-Android/1.0")
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyStr = response.body?.string() ?: ""
                    val root = JSONObject(bodyStr)
                    val tweet = root.optJSONObject("tweet")
                    if (tweet != null) {
                        var rawText = tweet.optString("text", "")
                        // Remove trailing t.co links
                        rawText = rawText.replace(Regex("https?://t\\.co/\\S+"), "").trim()

                        val author = tweet.optJSONObject("author")
                        val authorName = author?.optString("name", "User") ?: "User"
                        val authorHandle = "@" + (author?.optString("screen_name", "user")?.removePrefix("@") ?: "user")
                        val avatarUrl = author?.optString("avatar_url", null)

                        val mediaList = mutableListOf<String>()
                        val mediaObj = tweet.optJSONObject("media")
                        val photos = mediaObj?.optJSONArray("photos")
                        if (photos != null) {
                            for (i in 0 until photos.length()) {
                                val p = photos.getJSONObject(i)
                                val pUrl = p.optString("url")
                                if (pUrl.isNotBlank()) mediaList.add(pUrl)
                            }
                        }
                        // If no photos, check videos for 1st frame thumbnail
                        if (mediaList.isEmpty()) {
                            val videos = mediaObj?.optJSONArray("videos")
                            if (videos != null && videos.length() > 0) {
                                val v = videos.getJSONObject(0)
                                val thumb = v.optString("thumbnail_url")
                                if (thumb.isNotBlank()) mediaList.add(thumb)
                            } else {
                                val video = mediaObj?.optJSONObject("video")
                                val thumb = video?.optString("thumbnail_url")
                                if (!thumb.isNullOrBlank()) mediaList.add(thumb)
                            }
                        }

                        return@withContext TweetData(
                            id = id,
                            text = rawText,
                            authorName = authorName,
                            authorHandle = authorHandle,
                            avatarUrl = avatarUrl,
                            isVerified = false,
                            mediaUrls = mediaList
                        )
                    }
                }
            }
        } catch (_: Exception) {
            // Fall through to backup
        }

        // 2. Fallback to Twitter Syndication endpoint
        val synUrl = "https://cdn.syndication.twimg.com/tweet-result?id=$id&token=0"
        val synReq = Request.Builder()
            .url(synUrl)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
            .build()

        client.newCall(synReq).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("Failed to load post. Check the link and your connection.")
            }
            val body = response.body?.string() ?: throw IllegalStateException("Empty response")
            val root = JSONObject(body)
            var text = root.optString("text", "")
            val user = root.optJSONObject("user")
            val name = user?.optString("name", "User") ?: "User"
            val handle = "@" + (user?.optString("screen_name", "user") ?: "user")
            val avatar = user?.optString("profile_image_url_https", null)

            val mediaList = mutableListOf<String>()
            val mediaDetails = root.optJSONArray("mediaDetails")
            if (mediaDetails != null) {
                for (i in 0 until mediaDetails.length()) {
                    val m = mediaDetails.getJSONObject(i)
                    val mUrl = m.optString("media_url_https")
                    if (mUrl.isNotBlank()) mediaList.add(mUrl)
                }
            }
            text = text.replace(Regex("https?://t\\.co/\\S+"), "").trim()

            TweetData(
                id = id,
                text = text,
                authorName = name,
                authorHandle = handle,
                avatarUrl = avatar,
                isVerified = user?.optBoolean("is_blue_verified", false) ?: false,
                mediaUrls = mediaList
            )
        }
    }
}
