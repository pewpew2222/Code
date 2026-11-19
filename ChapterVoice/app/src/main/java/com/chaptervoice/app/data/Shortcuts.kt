package com.chaptervoice.app.data

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.chaptervoice.app.MainActivity
import com.chaptervoice.app.R

/**
 * App icon shortcuts (hold the app icon on the home screen): "Continue: <book>" for the
 * most recent books. Each starts reading where you left off.
 */
object Shortcuts {
    const val ACTION_CONTINUE = "com.chaptervoice.CONTINUE"
    const val EXTRA_URL = "url"
    private const val MAX = 3

    fun update(context: Context, books: List<Book>) {
        runCatching {
            val shortcuts = books.take(MAX).mapIndexed { rank, book ->
                val cover = book.coverUrl?.let { ImageCache.cached(it) }
                val icon = if (cover != null) IconCompat.createWithBitmap(cover) else IconCompat.createWithResource(context, R.mipmap.ic_launcher)
                ShortcutInfoCompat.Builder(context, "book-" + book.key.hashCode())
                    .setShortLabel(book.name.take(24))
                    .setLongLabel(context.getString(R.string.shortcut_continue, book.name).take(40))
                    .setIcon(icon)
                    .setRank(rank)
                    .setIntent(
                        Intent(context, MainActivity::class.java)
                            .setAction(ACTION_CONTINUE)
                            .putExtra(EXTRA_URL, book.lastChapterUrl)
                            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP),
                    )
                    .build()
            }
            ShortcutManagerCompat.setDynamicShortcuts(context, shortcuts)
        }
    }
}
