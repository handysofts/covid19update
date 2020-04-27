package com.yoump34.covid19update

import android.app.NotificationChannel
import android.app.NotificationManager
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.BlendMode
import android.graphics.BlendModeColorFilter
import android.graphics.PorterDuff
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.work.BackoffPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.MobileAds
import com.google.android.material.snackbar.Snackbar
import com.yoump34.covid19update.models.Country
import com.yoump34.covid19update.tasks.CheckForUpdateWorker
import com.yoump34.covid19update.tasks.CheckForUpdateWorker.Companion.parseForCountryUpdates
import com.yoump34.covid19update.tasks.CoVidUpdatesAppWidgetProvider
import com.yoump34.covid19update.utils.*
import kotlinx.android.synthetic.main.activity_main.*
import org.jetbrains.anko.browse
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.uiThread
import java.net.URL
import java.util.concurrent.TimeUnit


class MainActivity : AppCompatActivity() {

    private val progressBarLoading: ProgressBar by lazy {
        findViewById<ProgressBar>(R.id.progressBarLoading)
    }
    private val layoutUpdates: View by lazy {
        findViewById<View>(R.id.layoutUpdates)
    }
    private val adapter: ArrayAdapter<Country> by lazy {
        ArrayAdapter(applicationContext, android.R.layout.simple_spinner_item, countries)
    }

    private val countries = ArrayList<Country>()
    private var selectedCountry: Country? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        init()
        initAds()
    }

    override fun onDestroy() {
        val checkForUpdateWorkerRequest =
            PeriodicWorkRequestBuilder<CheckForUpdateWorker>(30, TimeUnit.MINUTES)
                .setInitialDelay(15, TimeUnit.MINUTES)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 5, TimeUnit.MINUTES)
                .build()
        WorkManager.getInstance(this).enqueue(checkForUpdateWorkerRequest)

        super.onDestroy()
    }

    private fun initAds() {
        MobileAds.initialize(this) {}
        val mAdView: AdView = findViewById(R.id.adView)
        val adRequest = AdRequest.Builder().build()
        mAdView.loadAd(adRequest)
    }


    private fun init() {
        findViewById<Button>(R.id.buttonMore).setOnClickListener {
            browse(GET_DATA_URL + (if (selectedCountry != null) selectedCountry?.uri else ""))
        }

        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)

        initSpinner()

        parseWorldOMeter()
        registerNotificationChannel()
    }

    private fun initSpinner() {
        val spinnerCountries: Spinner = findViewById(R.id.spinnerCountries)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            spinnerCountries.background.colorFilter =
                BlendModeColorFilter(android.R.color.black, BlendMode.SRC_ATOP)
        } else {
            @Suppress("DEPRECATION")
            spinnerCountries.background.setColorFilter(
                resources.getColor(android.R.color.black),
                PorterDuff.Mode.SRC_ATOP
            )
        }
        spinnerCountries.adapter = adapter
        spinnerCountries.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) {
                Log.v(TAG, "Nothing selected from spinner")
            }

            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                pos: Int,
                id: Long
            ) {
                Log.v(TAG, "Country at position $pos selected!")
                val textViewSpinnerSelectedItem = parent!!.getChildAt(0) as TextView
                textViewSpinnerSelectedItem.setTextColor(
                    ContextCompat.getColor(
                        applicationContext,
                        android.R.color.black
                    )
                )

                val country = countries[pos]
                Log.v(TAG, "Selected country is $country!")
                selectedCountry = country
                findViewById<TextView>(R.id.textViewTotalCasesLabel).text=getString(R.string.label_total_cases).plus(" ").plus(country.name)
                findViewById<TextView>(R.id.textViewTotalCases).text = country.totalCases
                findViewById<TextView>(R.id.textViewNewCases).text = country.newCases
                findViewById<TextView>(R.id.textViewTotalDeaths).text = country.totalDeaths
                findViewById<TextView>(R.id.textViewNewDeaths).text = country.newDeaths
                findViewById<TextView>(R.id.textViewTotalRecovered).text = country.totalRecovered

                val preferences = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                preferences.edit()
                    .putString(PREF_COUNTRY_NAME, country.name)
                    .putString(PREF_COUNTRY_NEW_CASES, country.newCases)
                    .putString(PREF_COUNTRY_NEW_DEATHS, country.newDeaths)
                    .apply()

                //Update all widgets
                val intent = Intent(this@MainActivity, CoVidUpdatesAppWidgetProvider::class.java)
                intent.action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                val ids = AppWidgetManager.getInstance(application).getAppWidgetIds(
                    ComponentName(
                        applicationContext, CoVidUpdatesAppWidgetProvider::class.java!!
                    )
                )
                intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                sendBroadcast(intent)
            }
        }
    }

    private fun registerNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelId = getChannelId(applicationContext)
            val channel =
                NotificationChannel(channelId, "covid", NotificationManager.IMPORTANCE_HIGH)
            channel.description = "Notification channel for CoVid-19"
            val notificationManager =
                applicationContext.getSystemService(NotificationManager::class.java)
            notificationManager?.createNotificationChannel(channel)

        }
    }

    private fun parseWorldOMeter() {
        progressBarLoading.visibility = View.VISIBLE
        layoutUpdates.visibility = View.GONE

        doAsync {

            try {
                val doc = parseForCountryUpdates(URL(GET_DATA_URL), countries)
                Log.v(TAG, "Loaded document:\n$doc")
                if (doc == null)
                    throw RuntimeException("failed to get document after given number of retry!")

                val mainStatElements = doc.select("div#maincounter-wrap")
                countries.add(
                    0,
                    Country(
                        "General info",
                        totalCases = mainStatElements[0].select("div.maincounter-number")[0].text(),
                        totalDeaths = mainStatElements[1].select("div.maincounter-number")[0].text(),
                        totalRecovered = mainStatElements[2].select("div.maincounter-number")[0].text()
                    )
                )


            } catch (e: Exception) {
                Log.e(TAG, "Failed to load data from site", e)
                Snackbar.make(layoutUpdates, "Failed to load data", Snackbar.LENGTH_INDEFINITE)
                    .setAction("Retry") {
                        parseWorldOMeter()
                    }.show()
            }

            uiThread {
                progressBarLoading.visibility = View.GONE
                layoutUpdates.visibility = View.VISIBLE

                adapter.notifyDataSetChanged()

                val countryName =
                    getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).getString(
                        PREF_COUNTRY_NAME,
                        ""
                    ) ?: ""
                if (countryName.isNotBlank()) {
                    spinnerCountries.setSelection(
                        countries.indexOfFirst { c -> c.name == countryName }
                    )
                }
            }
        }
    }
}