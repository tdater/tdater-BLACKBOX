package com.tdater.blackbox.system

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.quicksettings.TileService
import android.widget.RemoteViews
import com.tdater.blackbox.R
import com.tdater.blackbox.ui.MainActivity

/** Entry points that open BLACKBOX straight into a capture: home-screen widget and quick-settings tile. */
object QuickCapture {
    const val EXTRA_MODE = "com.tdater.blackbox.QUICK"
    const val NOTE = "note"
    const val PHOTO = "photo"
    const val VOICE = "voice"
    const val READING = "reading"
    const val SCAN = "scan"

    fun intent(context: Context, mode: String): Intent =
        Intent(context, MainActivity::class.java).setAction("com.tdater.blackbox.action.QUICK_$mode")
            .putExtra(EXTRA_MODE, mode).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

    fun pending(context: Context, mode: String): PendingIntent =
        PendingIntent.getActivity(context, mode.hashCode(), intent(context, mode), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
}

class QuickCaptureWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val views = RemoteViews(context.packageName, R.layout.widget_quick_capture).apply {
            setOnClickPendingIntent(R.id.widget_note, QuickCapture.pending(context, QuickCapture.NOTE))
            setOnClickPendingIntent(R.id.widget_photo, QuickCapture.pending(context, QuickCapture.PHOTO))
            setOnClickPendingIntent(R.id.widget_voice, QuickCapture.pending(context, QuickCapture.VOICE))
            setOnClickPendingIntent(R.id.widget_reading, QuickCapture.pending(context, QuickCapture.READING))
        }
        ids.forEach { manager.updateAppWidget(it, views) }
    }
}

class QuickNoteTileService : TileService() {
    override fun onClick() {
        super.onClick()
        if (Build.VERSION.SDK_INT >= 34) startActivityAndCollapse(QuickCapture.pending(this, QuickCapture.NOTE))
        else @Suppress("DEPRECATION") startActivityAndCollapse(QuickCapture.intent(this, QuickCapture.NOTE))
    }
}
