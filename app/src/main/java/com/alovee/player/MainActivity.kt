cd ~/AloVeePlayer || exit 1

cat > app/src/main/java/com/alovee/player/MainActivity.kt <<'EOF'
package com.alovee.player

import android.Manifest
import android.app.Activity
import android.content.ContentUris
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private var player: MediaPlayer? = null
    private val songs = mutableListOf<Pair<String, Uri>>()
    private var index = 0
    private lateinit var title: TextView
    private lateinit var status: TextView
    private lateinit var root: LinearLayout
    private lateinit var songList: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(8, 11, 20)
        window.navigationBarColor = Color.rgb(8, 11, 20)
        buildUI()
        checkPermission()
    }

    private fun buildUI() {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(24, 24, 24, 12)
            setBackgroundColor(Color.rgb(8, 11, 20))
        }

        fun label(value: String, size: Float, color: Int) =
            TextView(this).apply {
                text = value
                textSize = size
                setTextColor(color)
                gravity = Gravity.CENTER
            }

        root.addView(label("ALOVEE", 30f, Color.rgb(0, 229, 255)))
        root.addView(label("OFFLINE MUSIC PLAYER", 12f, Color.LTGRAY))

        val visual = label("〰  ♫  〰", 38f, Color.rgb(0, 229, 255))
        visual.setPadding(0, 18, 0, 18)
        root.addView(visual)

        title = label("Choose your music", 19f, Color.WHITE)
        root.addView(title)

        status = label("Your music. Your vibe.", 13f, Color.LTGRAY)
        status.setPadding(0, 8, 0, 12)
        root.addView(status)

        fun addButton(text: String, action: () -> Unit) {
            root.addView(Button(this).apply {
                this.text = text
                setTextColor(Color.WHITE)
                setBackgroundColor(Color.rgb(20, 35, 55))
                setOnClickListener { action() }
            }, LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = 4
            })
        }

        addButton("SCAN MUSIC") { loadSongs() }
        addButton("⏮ PREVIOUS") {
            if (songs.isNotEmpty()) play((index - 1 + songs.size) % songs.size)
        }
        addButton("▶ / ⏸ PLAY / PAUSE") {
            when {
                player?.isPlaying == true -> {
                    player?.pause()
                    status.text = "Paused"
                }
                player != null -> {
                    try {
                        player?.start()
                        status.text = "Playing"
                    } catch (_: Exception) {
                        play(index)
                    }
                }
                songs.isNotEmpty() -> play(index)
                else -> loadSongs()
            }
        }
        addButton("⏭ NEXT") {
            if (songs.isNotEmpty()) play((index + 1) % songs.size)
        }

        val scroll = android.widget.ScrollView(this)
        songList = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        scroll.addView(songList)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
    }

    private fun checkPermission() {
        val permission = if (Build.VERSION.SDK_INT >= 33)
            Manifest.permission.READ_MEDIA_AUDIO
        else Manifest.permission.READ_EXTERNAL_STORAGE

        if (checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(permission), 100)
        } else {
            loadSongs()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100 && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            loadSongs()
        } else {
            status.text = "Allow audio access to scan music"
        }
    }

    private fun loadSongs() {
        songs.clear()
        songList.removeAllViews()

        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE
        )

        try {
            contentResolver.query(
                collection,
                projection,
                "${MediaStore.Audio.Media.IS_MUSIC} != 0",
                null,
                "${MediaStore.Audio.Media.TITLE} ASC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val name = cursor.getString(nameCol) ?: "Unknown track"
                    songs.add(name to ContentUris.withAppendedId(collection, id))
                }
            }
        } catch (_: Exception) {
            status.text = "Music scan failed"
            return
        }

        status.text = "${songs.size} tracks found"

        songs.forEachIndexed { position, song ->
            songList.addView(TextView(this).apply {
                text = "${position + 1}. ${song.first}"
                textSize = 15f
                setTextColor(Color.WHITE)
                setPadding(12, 14, 12, 14)
                setOnClickListener { play(position) }
            })
        }

        title.text = songs.firstOrNull()?.first ?: "No music found"
        Toast.makeText(this, "${songs.size} tracks found", Toast.LENGTH_SHORT).show()
    }

    private fun play(position: Int) {
        if (songs.isEmpty()) return
        index = position.coerceIn(0, songs.lastIndex)

        player?.release()
        player = null

        try {
            player = MediaPlayer().apply {
                setDataSource(this@MainActivity, songs[index].second)
                setOnPreparedListener {
                    it.start()
                    title.text = songs[index].first
                    status.text = "Playing ${index + 1}/${songs.size}"
                }
                setOnCompletionListener {
                    if (songs.size > 1) play((index + 1) % songs.size)
                }
                setOnErrorListener { _, _, _ ->
                    status.text = "Unable to play this track"
                    true
                }
                prepareAsync()
            }
            title.text = songs[index].first
            status.text = "Loading track..."
        } catch (_: Exception) {
            status.text = "Playback error"
        }
    }

    override fun onDestroy() {
        player?.release()
        player = null
        super.onDestroy()
    }
}
EOF

test -s app/src/main/java/com/alovee/player/MainActivity.kt && echo "SUCCESS: MainActivity.kt created" || echo "ERROR: file missing"
