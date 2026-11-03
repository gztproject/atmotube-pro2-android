package com.atmotube.pro2.example

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import java.util.Locale

class AirWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val views = renderStoredWidget(context)
        for (id in appWidgetIds) {
            appWidgetManager.updateAppWidget(id, views)
        }
    }

    companion object {
        fun publish(context: Context, lines: List<WidgetLine>, updatedAtMillis: Long) {
            context.getSharedPreferences(AirStore.PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(AirStore.KEY_WIDGET_LINES, encodeWidgetLines(lines))
                .putLong(AirStore.KEY_WIDGET_UPDATED, updatedAtMillis)
                .apply()
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, AirWidgetProvider::class.java))
            if (ids.isEmpty()) return
            val views = renderWidget(context, lines, updatedAtMillis)
            for (id in ids) manager.updateAppWidget(id, views)
        }
    }
}

internal fun renderStoredWidget(context: Context): RemoteViews {
    val prefs = context.getSharedPreferences(AirStore.PREFS, Context.MODE_PRIVATE)
    val updated = prefs.getLong(AirStore.KEY_WIDGET_UPDATED, 0L).takeIf { it > 0L }
    return renderWidget(context, decodeWidgetLines(prefs.getString(AirStore.KEY_WIDGET_LINES, null)), updated)
}

internal fun renderWidget(context: Context, lines: List<WidgetLine>, updatedAtMillis: Long?): RemoteViews {
    val views = RemoteViews(context.packageName, R.layout.air_widget)
    views.setTextViewText(
        R.id.widget_status,
        widgetFreshness(updatedAtMillis, System.currentTimeMillis(), Locale.getDefault()),
    )
    val shown = lines.ifEmpty {
        listOf(WidgetLine("Atmotube", "—", "Connect to show readings", AirLevel.UNKNOWN))
    }
    val rows = intArrayOf(R.id.widget_row_0, R.id.widget_row_1, R.id.widget_row_2, R.id.widget_row_3, R.id.widget_row_4, R.id.widget_row_5)
    val labels = intArrayOf(R.id.widget_label_0, R.id.widget_label_1, R.id.widget_label_2, R.id.widget_label_3, R.id.widget_label_4, R.id.widget_label_5)
    val values = intArrayOf(R.id.widget_value_0, R.id.widget_value_1, R.id.widget_value_2, R.id.widget_value_3, R.id.widget_value_4, R.id.widget_value_5)
    val details = intArrayOf(R.id.widget_detail_0, R.id.widget_detail_1, R.id.widget_detail_2, R.id.widget_detail_3, R.id.widget_detail_4, R.id.widget_detail_5)
    for (index in rows.indices) {
        val line = shown.getOrNull(index)
        if (line == null) {
            views.setViewVisibility(rows[index], View.GONE)
        } else {
            views.setViewVisibility(rows[index], View.VISIBLE)
            views.setTextViewText(labels[index], line.label)
            views.setTextViewText(values[index], line.value)
            views.setTextViewText(details[index], line.detail)
            views.setTextColor(details[index], line.level.widgetColor())
        }
    }
    val open = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
    views.setOnClickPendingIntent(R.id.widget_root, open)
    return views
}
