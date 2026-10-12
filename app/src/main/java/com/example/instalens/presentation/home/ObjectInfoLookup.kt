package com.example.instalens.presentation.home

import android.util.Log
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

internal fun lookupOnlineObjectInfo(label: String): String? {
    val title = label.split(' ').joinToString(" ") { word ->
        word.replaceFirstChar { it.uppercase() }
    }
    val encodedTitle = URLEncoder.encode(title, Charsets.UTF_8.name()).replace("+", "_")
    return try {
        val connection = URL("https://en.wikipedia.org/api/rest_v1/page/summary/$encodedTitle")
            .openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 1_800
            connection.readTimeout = 1_800
            connection.setRequestProperty("User-Agent", "InstaLens/1.0 (Android object reference)")
            val status = connection.responseCode
            if (status !in 200..299) {
                Log.i("ObjectInfoLookup", "No online reference for label '$label' (HTTP $status)")
                null
            } else {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                JSONObject(response).optString("extract").takeIf(String::isNotBlank)
            }
        } finally {
            connection.disconnect()
        }
    } catch (exception: IOException) {
        Log.i("ObjectInfoLookup", "Online reference unavailable for label '$label'", exception)
        null
    } catch (exception: JSONException) {
        Log.w("ObjectInfoLookup", "Online reference response was invalid for label '$label'", exception)
        null
    }
}
