package com.example.sonicwavev4.ui.music.adapters

import android.content.ClipData
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.sonicwavev4.R
import com.example.sonicwavev4.ui.music.SongRowUi

data class DragPayload(
    val songKey: String,
    val title: String,
    val artist: String,
    val playableUriString: String,
    val isRemote: Boolean,
    val isDownloaded: Boolean,
    val downloadedLocalUriString: String?
)

class SongListAdapter(
    private val onSongClick: (SongRowUi) -> Unit,
    private val onDownloadClick: (SongRowUi) -> Unit,
    private val onSongLongPress: (SongRowUi) -> Unit
) : ListAdapter<SongRowUi, SongListAdapter.SongViewHolder>(DiffCallback) {

    var currentPlayingUriString: String? = null
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SongViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_song, parent, false)
        return SongViewHolder(view, onSongClick, onDownloadClick, onSongLongPress)
    }

    override fun onBindViewHolder(holder: SongViewHolder, position: Int) {
        val item = getItem(position)
        val isPlaying = item.playUriString == currentPlayingUriString
        holder.bind(item, isPlaying)
    }

    class SongViewHolder(
        itemView: View,
        private val onSongClick: (SongRowUi) -> Unit,
        private val onDownloadClick: (SongRowUi) -> Unit,
        private val onSongLongPress: (SongRowUi) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {

        private val title: TextView = itemView.findViewById(R.id.tvSongTitle)
        private val downloadIcon: ImageView = itemView.findViewById(R.id.ivDownloadStatus)

        private val downloadedColor: Int = title.context.getColor(R.color.black)
        private val pendingDownloadColor: Int = title.context.getColor(R.color.nav_item_inactive)
        private val normalBackground = itemView.context.getDrawable(R.drawable.bg_song_item)
        private val selectedBackground = itemView.context.getDrawable(R.drawable.bg_song_item_selected)
        private val touchSlop = ViewConfiguration.get(itemView.context).scaledTouchSlop
        private var longPressArmed = false
        private var dragStarted = false
        private var downX = 0f
        private var downY = 0f
        private val gestureDetector = GestureDetector(
            itemView.context,
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onDown(e: MotionEvent): Boolean {
                    return true
                }

                override fun onLongPress(e: MotionEvent) {
                    longPressArmed = true
                    downX = e.x
                    downY = e.y
                }
            }
        )

        fun bind(item: SongRowUi, playing: Boolean) {
            title.text = item.title

            val baseColor = if (item.isDownloaded || !item.isRemote) downloadedColor else pendingDownloadColor
            title.setTextColor(baseColor)

            itemView.background = if (playing) selectedBackground else normalBackground

            if (item.isRemote) {
                downloadIcon.visibility = View.VISIBLE
                if (item.isDownloaded) {
                    downloadIcon.setImageResource(R.drawable.ic_music_download_done)
                    downloadIcon.imageAlpha = 255
                    downloadIcon.setOnClickListener(null)
                } else {
                    downloadIcon.setImageResource(R.drawable.ic_music_download)
                    downloadIcon.imageAlpha = 200
                    downloadIcon.setOnClickListener { onDownloadClick(item) }
                }
            } else {
                downloadIcon.visibility = View.GONE
                downloadIcon.setOnClickListener(null)
            }

            itemView.setOnClickListener { onSongClick(item) }
            itemView.setOnTouchListener { v, event ->
                gestureDetector.onTouchEvent(event)
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        dragStarted = false
                        longPressArmed = false
                        downX = event.x
                        downY = event.y
                        false
                    }

                    MotionEvent.ACTION_MOVE -> {
                        if (longPressArmed && !dragStarted) {
                            val dx = event.x - downX
                            val dy = event.y - downY
                            if (kotlin.math.hypot(dx.toDouble(), dy.toDouble()) > touchSlop) {
                                dragStarted = startDragIfAllowed(v, item, downX, downY)
                                if (!dragStarted) {
                                    longPressArmed = false
                                }
                                true
                            } else {
                                false
                            }
                        } else {
                            false
                        }
                    }

                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        if (longPressArmed && !dragStarted) {
                            onSongLongPress(item)
                            longPressArmed = false
                            true
                        } else {
                            longPressArmed = false
                            dragStarted = false
                            false
                        }
                    }

                    else -> false
                }
            }
        }

        private fun startDragIfAllowed(view: View, item: SongRowUi, touchX: Float, touchY: Float): Boolean {
            if (item.isRemote && !item.isDownloaded) {
                Toast.makeText(view.context, "请先下载后再添加到歌单", Toast.LENGTH_SHORT).show()
                return false
            }
            val payload = DragPayload(
                songKey = item.key,
                title = item.title,
                artist = item.artist,
                playableUriString = item.playUriString,
                isRemote = item.isRemote,
                isDownloaded = item.isDownloaded,
                downloadedLocalUriString = item.downloadedLocalUriString
            )
            val clip = ClipData.newPlainText("song", payload.playableUriString)
            val shadow = DotDragShadowBuilder(view)
            showDotPulse(view, touchX, touchY)
            val success = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                view.startDragAndDrop(clip, shadow, payload, 0)
            } else {
                @Suppress("DEPRECATION")
                view.startDrag(clip, shadow, payload, 0)
            }
            return success
        }

        private fun showDotPulse(view: View, centerX: Float, centerY: Float) {
            val drawable = DotPulseDrawable(centerX, centerY)
            view.overlay.add(drawable)
            val animator = ValueAnimator.ofFloat(18f, 6f)
            animator.duration = 140
            animator.addUpdateListener {
                drawable.radius = it.animatedValue as Float
                drawable.invalidateSelf()
            }
            animator.addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    view.overlay.remove(drawable)
                }
            })
            animator.start()
        }
    }

    private class DotDragShadowBuilder(view: View) : View.DragShadowBuilder(view) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#CC000000")
        }
        private val radius = 12f

        override fun onProvideShadowMetrics(outShadowSize: android.graphics.Point, outShadowTouchPoint: android.graphics.Point) {
            val size = (radius * 2).toInt()
            outShadowSize.set(size, size)
            outShadowTouchPoint.set(size / 2, size / 2)
        }

        override fun onDrawShadow(canvas: Canvas) {
            canvas.drawCircle(radius, radius, radius, paint)
        }
    }

    private class DotPulseDrawable(
        private val centerX: Float,
        private val centerY: Float
    ) : Drawable() {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#99000000")
        }
        var radius: Float = 18f

        override fun draw(canvas: Canvas) {
            canvas.drawCircle(centerX, centerY, radius, paint)
        }

        override fun setAlpha(alpha: Int) {
            paint.alpha = alpha
        }

        override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) {
            paint.colorFilter = colorFilter
        }

        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }

    private object DiffCallback : DiffUtil.ItemCallback<SongRowUi>() {
        override fun areItemsTheSame(oldItem: SongRowUi, newItem: SongRowUi): Boolean =
            oldItem.key == newItem.key

        override fun areContentsTheSame(oldItem: SongRowUi, newItem: SongRowUi): Boolean =
            oldItem == newItem
    }
}
