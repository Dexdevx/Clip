package com.xshot.app

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.xshot.app.databinding.ActivityMainBinding
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var currentBitmap: Bitmap? = null
    private var lastSavedUri: Uri? = null
    private var currentStyle = CardRenderer.Style.HEADLINE_ONLY
    private var is45Ratio = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupListeners()
        handleIncomingIntent(intent)

        // Load GTA 6 initial sample
        binding.etUrl.setText("https://x.com/gtasix_/status/2121519137667027013")
        generateCard()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        intent?.let { handleIncomingIntent(it) }
    }

    private fun handleIncomingIntent(intent: Intent) {
        if (intent.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
            if (!sharedText.isNullOrBlank()) {
                binding.etUrl.setText(sharedText)
                generateCard()
            }
        }
    }

    private fun setupListeners() {
        // Clipboard action button: auto-detects copied link from clipboard and generates
        binding.btnGenerate.setOnClickListener {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = clipboard.primaryClip
            if (clip != null && clip.itemCount > 0) {
                val clipText = clip.getItemAt(0).text?.toString()?.trim() ?: ""
                if (clipText.contains("twitter.com") || clipText.contains("x.com") || clipText.matches(Regex("^\\d+$"))) {
                    binding.etUrl.setText(clipText)
                    Toast.makeText(this, "Link detected from clipboard!", Toast.LENGTH_SHORT).show()
                }
            }
            generateCard()
        }

        // Keyboard Done / Enter action
        binding.etUrl.setOnEditorActionListener { _, actionId, _ ->
            generateCard()
            true
        }

        // Mode: Snapshot
        binding.cardModeSnapshot.setOnClickListener {
            currentStyle = CardRenderer.Style.HEADLINE_ONLY
            binding.cardModeSnapshot.strokeWidth = dpToPx(2)
            binding.cardModeDouble.strokeWidth = 0
            binding.cardModeVideo.strokeWidth = 0
            binding.sectionTweet2.visibility = View.GONE
            binding.btnSaveTop.text = "Save"
            generateCard()
        }

        // Mode: Double
        binding.cardModeDouble.setOnClickListener {
            binding.cardModeDouble.strokeWidth = dpToPx(2)
            binding.cardModeSnapshot.strokeWidth = 0
            binding.cardModeVideo.strokeWidth = 0
            binding.sectionTweet2.visibility = View.VISIBLE
            binding.btnSaveTop.text = "Save"
            generateCard()
        }

        // Mode: Video
        binding.cardModeVideo.setOnClickListener {
            binding.cardModeVideo.strokeWidth = dpToPx(2)
            binding.cardModeSnapshot.strokeWidth = 0
            binding.cardModeDouble.strokeWidth = 0
            binding.sectionTweet2.visibility = View.GONE
            binding.btnSaveTop.text = "Save MP4"
            generateCard()
        }

        // Top Save button
        binding.btnSaveTop.setOnClickListener {
            val bmp = currentBitmap ?: return@setOnClickListener
            lifecycleScope.launch {
                val uri = CardRenderer.saveToGallery(this@MainActivity, bmp)
                if (uri != null) {
                    lastSavedUri = uri
                    Toast.makeText(this@MainActivity, "Saved to Photos / Pictures/XPostShot!", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this@MainActivity, "Failed to save image", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun generateCard() {
        val url = binding.etUrl.text.toString().trim()
        if (url.isEmpty()) return

        binding.clipboardIcon.visibility = View.GONE
        binding.btnSpinner.visibility = View.VISIBLE

        lifecycleScope.launch {
            try {
                val tweet = TweetFetcher.fetch(url)

                val bitmap = CardRenderer.renderCard(
                    context = this@MainActivity,
                    tweet = tweet,
                    style = currentStyle,
                    is45Ratio = is45Ratio
                )

                currentBitmap = bitmap
                lastSavedUri = null

                binding.ivPreview.setImageBitmap(bitmap)
                binding.previewContainer.visibility = View.VISIBLE

            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Error: ${e.localizedMessage ?: "Failed to load"}", Toast.LENGTH_LONG).show()
            } finally {
                binding.clipboardIcon.visibility = View.VISIBLE
                binding.btnSpinner.visibility = View.GONE
            }
        }
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }
}
