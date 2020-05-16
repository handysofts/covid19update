package com.yoump34.covid19update.tasks

import android.app.PendingIntent
import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkInfo
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.yoump34.covid19update.MainActivity
import com.yoump34.covid19update.models.Country
import com.yoump34.covid19update.utils.*
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URL
import java.util.*
import kotlin.collections.ArrayList

/**
 *
 * Created by Vasif Mustafayev on 2020-03-16 15:38
 * vmustafayev@gmail.com
 */
class CheckForUpdateWorker(private val appContext: Context, workerParams: WorkerParameters) :
    Worker(appContext, workerParams) {

    override fun doWork(): Result {
        Log.v(TAG, "Check site if there is any update and show notification")

        try {
            if (!isConnectedOrConnecting(appContext))
                return Result.retry()

            val sharedPreferences = appContext.getSharedPreferences(PREF_NAME, MODE_PRIVATE)
            val countryName = sharedPreferences.getString(PREF_COUNTRY_NAME, "") ?: ""
            if (countryName.isBlank())
                return Result.success()

            val countries = ArrayList<Country>()
            parseForCountryUpdates(URL(GET_DATA_URL), countries)
            val country = countries.first { c ->
                countryName == c.name
            }

            if (country.newCases != sharedPreferences.getString(
                    PREF_COUNTRY_NEW_CASES,
                    ""
                ) || country.newDeaths != sharedPreferences.getString(
                    PREF_COUNTRY_NEW_DEATHS, ""
                )
            ) {
                Log.v(TAG, "New cases or deaths found!")

                sharedPreferences.edit()
                    .putString(PREF_COUNTRY_NEW_CASES, country.newCases)
                    .putString(PREF_COUNTRY_NEW_DEATHS, country.newDeaths)
                    .apply()


                with(NotificationManagerCompat.from(appContext)) {
                    notify(
                        1001,
                        NotificationCompat.Builder(applicationContext, getChannelId(appContext))
                            .setSmallIcon(android.R.drawable.ic_dialog_info)
                            .setContentIntent(
                                PendingIntent.getActivity(
                                    appContext,
                                    1,
                                    Intent(appContext, MainActivity::class.java),
                                    PendingIntent.FLAG_UPDATE_CURRENT
                                )
                            )
                            .setContentTitle("New update found")
                            .setContentText("${country.newCases} new cases and ${country.newDeaths} new deaths in ${country.name}")
                            .setPriority(NotificationCompat.DEFAULT_ALL)
                            .setAutoCancel(true)
                            .build()
                    )
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Failed to load data in bg", e)
            return Result.retry()
        }

        return Result.success()
    }

    private fun isConnectedOrConnecting(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork: NetworkInfo? = cm.activeNetworkInfo
        return activeNetwork?.isConnectedOrConnecting == true
    }


    companion object {
        fun parseForCountryUpdates(url: URL, countries: ArrayList<Country>): Document? {
            var doc: Document? = null
            var retryCount = 20
            while (doc == null && retryCount > 0) {
                try {
                    Log.v(TAG, "Trying to get CoVid data retry: $retryCount")
                    doc = Jsoup.parse(url, 45 * 1000)
                } catch (e: Exception) {
                    Log.e(TAG, "Can't get page data, let's try again")
                    Thread.sleep(15 * 1000)
                    retryCount--
                }
            }

            if (doc == null)
                return null

            val statsTable = doc.select("table#main_table_countries_today")
            statsTable.select("tbody").select("tr").forEach {
                val cols = it.select("td")
                val countryNameCol = cols[1]
                if (countryNameCol.text().isNotBlank() && !countryNameCol.text().toLowerCase(Locale.ENGLISH)
                        .contains("total")
                ) {
                    val uri: Element? = countryNameCol.select("a").first()
                    val country = Country(
                        name = countryNameCol.text(),
                        uri = uri?.attr("href") ?: "",
                        totalCases = cols[2].text(),
                        newCases = cols[3].text().replace("+", ""),
                        totalDeaths = cols[4].text(),
                        newDeaths = cols[5].text().replace("+", ""),
                        totalRecovered = cols[6].text()
                    )
                    countries.add(country)
                }
            }
            return doc
        }
    }
}