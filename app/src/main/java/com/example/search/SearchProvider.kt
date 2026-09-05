package com.example.search

data class SearchResultItem(
    val title: String,
    val snippet: String,
    val url: String
)

data class SearchResponse(
    val query: String,
    val items: List<SearchResultItem>,
    val provider: String,
    val error: String? = null
) {
    val summary: String
        get() {
            if (error != null) return "Search error: $error"
            if (items.isEmpty()) return "No results found for '$query'."
            return items.take(4).joinToString("\n\n") { "${it.title}: ${it.snippet}" }
        }
}

interface SearchProvider {
    val id: String
    val name: String
    val requiresApiKey: Boolean
    suspend fun search(query: String, apiKey: String = ""): SearchResponse
}
