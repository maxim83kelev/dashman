package cz.kelev.dashman

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

class DashmanWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (widgetId in appWidgetIds) {
            val intent = Intent(context, VoiceCaptureActivity::class.java)

            val pendingIntent = PendingIntent.getActivity(
                context,
                widgetId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val views = RemoteViews(context.packageName, R.layout.widget_dashman)

            views.setImageViewResource(
                R.id.widget_button,
                getWidgetIcon(context)
            )

            views.setOnClickPendingIntent(R.id.widget_button, pendingIntent)

            appWidgetManager.updateAppWidget(widgetId, views)
        }
    }

    private fun getWidgetIcon(context: Context): Int {
        val prefs = context.getSharedPreferences("dashman_prefs", Context.MODE_PRIVATE)
        val icon = prefs.getString("widget_icon", "tap_me") ?: "tap_me"

        return when (icon) {
            "death_mic" -> R.drawable.death_mic
            "in_my_hand" -> R.drawable.in_my_hand
            else -> R.drawable.tap_me
        }
    }

    fun refreshDashmanWidget(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val component = ComponentName(context, DashmanWidgetProvider::class.java)
        val ids = manager.getAppWidgetIds(component)

        for (id in ids) {
            val views = RemoteViews(context.packageName, R.layout.widget_dashman)

            views.setImageViewResource(
                R.id.widget_button,
                getWidgetIcon(context)
            )

            manager.updateAppWidget(id, views)
        }
    }
}