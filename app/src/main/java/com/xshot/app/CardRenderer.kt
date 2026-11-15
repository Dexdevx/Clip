package com.xshot.app

import android.content.ContentValues
import android.content.Context
import android.graphics.*
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

object CardRenderer {

    private val client = OkHttpClient()

    enum class AspectRatio(val width: Int, val height: Int) {
        RATIO_1_1(1080, 1080),
        RATIO_3_4(1080, 1440),
        RATIO_4_5(1080, 1350),
        RATIO_9_16(1080, 1920)
    }

    suspend fun renderCard(
        context: Context,
        tweet: TweetData,
        replyTweet: TweetData? = null,
        aspectRatio: AspectRatio = AspectRatio.RATIO_4_5
    ): Bitmap = withContext(Dispatchers.IO) {
        val cardW = aspectRatio.width
        val cardH = aspectRatio.height

        val bitmap = Bitmap.createBitmap(cardW, cardH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // 1. Clean White Background
        canvas.drawColor(Color.WHITE)

        val mediaPadH = 52
        val mediaW = cardW - (mediaPadH * 2) // 976px
        val textInset = 24
        val textMaxW = mediaW - (textInset * 2) // 928px
        val textStartX = (mediaPadH + textInset).toFloat()

        var currentY = 70f

        // Optional Full Header (Avatar + Username)
        if (style == Style.FULL_POST) {
            val avatarSize = 90
            val avatarBitmap = tweet.avatarUrl?.let { downloadBitmap(it) }
            val avatarPaint = Paint(Paint.ANTI_ALIAS_FLAG)

            if (avatarBitmap != null) {
                val roundAvatar = getCircularBitmap(avatarBitmap, avatarSize)
                canvas.drawBitmap(roundAvatar, textStartX, currentY, avatarPaint)
            } else {
                avatarPaint.color = Color.parseColor("#E1E8ED")
                canvas.drawCircle(textStartX + avatarSize / 2f, currentY + avatarSize / 2f, avatarSize / 2f, avatarPaint)
            }

            val namePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#0F1419")
                textSize = 34f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val handlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#536471")
                textSize = 28f
                typeface = Typeface.DEFAULT
            }

            canvas.drawText(tweet.authorName, textStartX + avatarSize + 24f, currentY + 40f, namePaint)
            canvas.drawText(tweet.authorHandle, textStartX + avatarSize + 24f, currentY + 76f, handlePaint)

            currentY += avatarSize + 36f
        }

        // 2. Headline / Post Text (46f, unbolded / normal weight)
        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0F1419")
            textSize = 46f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            letterSpacing = -0.015f
        }

        val textLayout = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            StaticLayout.Builder.obtain(tweet.text, 0, tweet.text.length, textPaint, textMaxW)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(12f, 1.15f)
                .setIncludePad(false)
                .build()
        } else {
            @Suppress("DEPRECATION")
            StaticLayout(
                tweet.text,
                textPaint,
                textMaxW,
                Layout.Alignment.ALIGN_NORMAL,
                1.15f,
                12f,
                false
            )
        }

        canvas.save()
        canvas.translate(textStartX, currentY)
        textLayout.draw(canvas)
        canvas.restore()

        currentY += textLayout.height + 40f

        // 3. Media Rendering
        val mediaBitmaps = tweet.mediaUrls.mapNotNull { downloadBitmap(it) }

        if (mediaBitmaps.isNotEmpty()) {
            val availableHeight = cardH - currentY - 52f // Bottom padding 52px
            val cornerRadius = 32f

            if (mediaBitmaps.size == 1) {
                // Single large image with rounded corners
                val img = mediaBitmaps[0]
                val targetH = availableHeight.coerceAtMost(mediaW * 1.1f).toInt()
                val rounded = getRoundedCornerBitmap(cropAndScale(img, mediaW, targetH), cornerRadius)
                canvas.drawBitmap(rounded, mediaPadH.toFloat(), currentY, null)

            } else if (mediaBitmaps.size >= 2) {
                // Two images side-by-side with rounded corners (matches reference image)
                val gap = 18
                val eachW = (mediaW - gap) / 2
                val targetH = availableHeight.coerceAtMost(eachW * 1.15f).toInt()

                val img1 = getRoundedCornerBitmap(cropAndScale(mediaBitmaps[0], eachW, targetH), cornerRadius)
                val img2 = getRoundedCornerBitmap(cropAndScale(mediaBitmaps[1], eachW, targetH), cornerRadius)

                canvas.drawBitmap(img1, mediaPadH.toFloat(), currentY, null)
                canvas.drawBitmap(img2, (mediaPadH + eachW + gap).toFloat(), currentY, null)
            }
        }

        bitmap
    }

    private fun downloadBitmap(url: String): Bitmap? {
        return try {
            val req = Request.Builder().url(url).build()
            client.newCall(req).execute().use { res ->
                if (res.isSuccessful) {
                    val stream = res.body?.byteStream()
                    BitmapFactory.decodeStream(stream)
                } else null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun cropAndScale(source: Bitmap, targetWidth: Int, targetHeight: Int): Bitmap {
        val srcW = source.width.toFloat()
        val srcH = source.height.toFloat()

        val scale = (targetWidth / srcW).coerceAtLeast(targetHeight / srcH)
        val scaledW = srcW * scale
        val scaledH = srcH * scale

        val left = (scaledW - targetWidth) / 2f
        val top = (scaledH - targetHeight) / 2f

        val output = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)

        val destRect = RectF(-left, -top, -left + scaledW, -top + scaledH)
        canvas.drawBitmap(source, null, destRect, paint)
        return output
    }

    private fun getRoundedCornerBitmap(bitmap: Bitmap, cornerRadius: Float): Bitmap {
        val output = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val rect = Rect(0, 0, bitmap.width, bitmap.height)
        val rectF = RectF(rect)

        canvas.drawRoundRect(rectF, cornerRadius, cornerRadius, paint)
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(bitmap, rect, rect, paint)
        return output
    }

    private fun getCircularBitmap(bitmap: Bitmap, size: Int): Bitmap {
        val scaled = Bitmap.createScaledBitmap(bitmap, size, size, true)
        val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val rect = Rect(0, 0, size, size)

        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(scaled, rect, rect, paint)
        return output
    }

    suspend fun saveToGallery(context: Context, bitmap: Bitmap): Uri? = withContext(Dispatchers.IO) {
        val filename = "XShot_${System.currentTimeMillis()}.png"
        var fos: OutputStream? = null
        var imageUri: Uri? = null

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/XPostShot")
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }

                imageUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                if (imageUri != null) {
                    fos = resolver.openOutputStream(imageUri)
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos!!)
                    contentValues.clear()
                    contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                    resolver.update(imageUri, contentValues, null, null)
                }
            } else {
                val imagesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).toString() + "/XPostShot"
                val file = File(imagesDir)
                if (!file.exists()) file.mkdirs()
                val imageFile = File(file, filename)
                fos = FileOutputStream(imageFile)
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
                imageUri = Uri.fromFile(imageFile)
            }
        } catch (_: Exception) {
            imageUri = null
        } finally {
            fos?.close()
        }

        imageUri
    }
}
