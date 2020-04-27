package com.yoump34.covid19update.utils

import android.content.Context

/**
 *
 * Created by Vasif Mustafayev on 2020-03-16 10:52
 * vmustafayev@gmail.com
 */
const val TAG = "COVID19"
const val GET_DATA_URL = "https://www.worldometers.info/coronavirus/"


const val PREF_NAME = "pref_covid19"
const val PREF_COUNTRY_NAME = "country_name"
const val PREF_COUNTRY_NEW_CASES = "country_new_cases"
const val PREF_COUNTRY_NEW_DEATHS = "country_new_deaths"

fun getChannelId(applicationContext: Context) = "${applicationContext.packageName}-covid"