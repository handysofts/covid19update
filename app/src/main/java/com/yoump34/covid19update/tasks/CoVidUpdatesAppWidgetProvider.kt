package com.yoump34.covid19update.tasks

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.RemoteViews
import com.yoump34.covid19update.MainActivity
import com.yoump34.covid19update.R
import com.yoump34.covid19update.models.Country
import com.yoump34.covid19update.utils.GET_DATA_URL
import com.yoump34.covid19update.utils.PREF_COUNTRY_NAME
import com.yoump34.covid19update.utils.PREF_NAME
import com.yoump34.covid19update.utils.TAG
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.uiThread
import java.net.URL

/**
 *
 * Created by Vasif Mustafayev on 2020-04-26 16:43
 * vmustafayev@gmail.com
 */
class CoVidUpdatesAppWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        appWidgetIds.forEach { appWidgetId ->
            Log.v(TAG, "Starting update widget: $appWidgetId")

            val pendingIntent: PendingIntent = Intent(context, MainActivity::class.java)
                .let { intent ->
                    PendingIntent.getActivity(context, 0, intent, 0)
                }

            var country: Country? = null
            val views: RemoteViews = RemoteViews(
                context.packageName,
                R.layout.appwidget_updates
            ).apply {
                setOnClickPendingIntent(R.id.layoutUpdates, pendingIntent)
            }

            appWidgetManager.updateAppWidget(appWidgetId, views)


            doAsync {
                try {
                    val sharedPreferences =
                        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                    val countryName = sharedPreferences.getString(PREF_COUNTRY_NAME, "") ?: ""
                    if (countryName.isNotBlank()) {
                        val countries = ArrayList<Country>()
                        CheckForUpdateWorker.parseForCountryUpdates(URL(GET_DATA_URL), countries)
                        country = countries.first { c ->
                            countryName == c.name
                        }

                        Log.v(TAG, "Country updates in widget for $country")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to get country data", e)
                }

                uiThread {
                    views.setTextViewText(
                        R.id.textViewTotalCasesLabel,
                        context.getString(R.string.label_total_cases).plus(" ").plus(country?.name)
                    )
                    views.setTextViewText(R.id.textViewTotalCases, country?.totalCases)
                    views.setTextViewText(R.id.textViewNewCases, country?.newCases)
                    views.setTextViewText(R.id.textViewTotalDeaths, country?.totalDeaths)
                    views.setTextViewText(R.id.textViewNewDeaths, country?.newDeaths)
                    views.setTextViewText(R.id.textViewTotalRecovered, country?.totalRecovered)

                    appWidgetManager.updateAppWidget(appWidgetId, views)
                }
            }
        }
    }
}