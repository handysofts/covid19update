package com.yoump34.covid19update.models

/**
 *
 * Created by Vasif Mustafayev on 2020-03-16 10:24
 * vmustafayev@gmail.com
 */
data class Country(
    val name: String,
    val totalCases: String,
    val newCases: String = "",
    val totalDeaths: String = "",
    val newDeaths: String = "",
    val totalRecovered: String = "",
    val uri: String = ""
) {

    override fun toString(): String {
        return name
    }
}