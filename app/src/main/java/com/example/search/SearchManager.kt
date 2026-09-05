package com.example.search

import com.example.data.SnowPreferences

class SearchManager(private val preferences: SnowPreferences) {

    private val duckDuckGoProvider = DuckDuckGoSearchProvider()

    suspend fun performSearch(query: String): SearchResponse {
        val providerSetting = preferences.searchProvider
        if (providerSetting == SnowPreferences.SEARCH_PROVIDER_NONE) {
            return SearchResponse(
                query = query,
                items = emptyList(),
                provider = "NONE",
                error = "Web search is disabled in Settings. Please enable DuckDuckGo or configure a search provider in Settings."
            )
        }

        return duckDuckGoProvider.search(query)
    }
}
