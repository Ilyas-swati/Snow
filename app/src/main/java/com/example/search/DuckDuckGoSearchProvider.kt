package com.example.search

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class DuckDuckGoSearchProvider : SearchProvider {

    override val id: String = "DUCKDUCKGO"
    override val name: String = "DuckDuckGo (Free & Instant)"
    override val requiresApiKey: Boolean = false

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    override suspend fun search(query: String, apiKey: String): SearchResponse = withContext(Dispatchers.IO) {
        val cleanQuery = query.trim()
        if (cleanQuery.isBlank()) {
            return@withContext SearchResponse(query, emptyList(), id, "Empty search query")
        }

        try {
            val encoded = URLEncoder.encode(cleanQuery, "UTF-8")
            // DuckDuckGo Instant Answer API
            val url = "https://api.duckduckgo.com/?q=$encoded&format=json&no_html=1&skip_disambig=1"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Android; Mobile; SnowAI/1.0)")
                .build()

            val response = client.newCall(request).execute()
            val raw = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                return@withContext SearchResponse(cleanQuery, emptyList(), id, "Search request returned code ${response.code}")
            }

            val items = mutableListOf<SearchResultItem>()
            val json = JSONObject(raw)

            val heading = json.optString("Heading", "")
            val abstractText = json.optString("AbstractText", "")
            val abstractUrl = json.optString("AbstractURL", "")

            if (abstractText.isNotBlank()) {
                items.add(SearchResultItem(heading.ifBlank { cleanQuery }, abstractText, abstractUrl))
            }

            val relatedTopics = json.optJSONArray("RelatedTopics")
            if (relatedTopics != null) {
                for (i in 0 until relatedTopics.length()) {
                    val topic = relatedTopics.optJSONObject(i) ?: continue
                    val text = topic.optString("Text", "")
                    val firstUrl = topic.optString("FirstURL", "")
                    if (text.isNotBlank()) {
                        val titlePart = text.substringBefore(" - ").take(60)
                        items.add(SearchResultItem(titlePart, text, firstUrl))
                    }
                    if (items.size >= 5) break
                }
            }

            if (items.isEmpty()) {
                // If instant answer was empty, return helpful fallback context
                items.add(
                    SearchResultItem(
                        title = cleanQuery,
                        snippet = "Web search was conducted for '$cleanQuery' on DuckDuckGo.",
                        url = "https://duckduckgo.com/?q=$encoded"
                    )
                )
            }

            SearchResponse(cleanQuery, items, id)
        } catch (e: Exception) {
            Log.e("DuckDuckGoSearch", "Search error", e)
            SearchResponse(cleanQuery, emptyList(), id, e.message ?: "Network error during search")
        }
    }
}
