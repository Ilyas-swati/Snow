package com.example.device

import android.content.Context
import android.database.Cursor
import android.provider.ContactsContract
import android.util.Log

data class ResolvedContact(
    val id: String,
    val displayName: String,
    val phoneNumber: String,
    val cleanNumber: String
)

sealed class ContactResolutionResult {
    data class Single(val contact: ResolvedContact) : ContactResolutionResult()
    data class Multiple(val nameQuery: String, val matches: List<ResolvedContact>) : ContactResolutionResult()
    data class NotFound(val nameQuery: String) : ContactResolutionResult()
    data class PermissionRequired(val message: String) : ContactResolutionResult()
}

class ContactResolver(private val context: Context) {

    fun resolveContact(query: String): ContactResolutionResult {
        val cleanQuery = query.trim()
        if (cleanQuery.isBlank()) {
            return ContactResolutionResult.NotFound(query)
        }

        val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )
        val selection = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?"
        val selectionArgs = arrayOf("%$cleanQuery%")

        val matches = mutableListOf<ResolvedContact>()
        var cursor: Cursor? = null

        try {
            cursor = context.contentResolver.query(uri, projection, selection, selectionArgs, null)
            cursor?.let {
                val idIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
                val nameIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)

                while (it.moveToNext() && matches.size < 10) {
                    val id = if (idIdx >= 0) it.getString(idIdx) ?: "" else ""
                    val name = if (nameIdx >= 0) it.getString(nameIdx) ?: "" else ""
                    val rawNumber = if (numIdx >= 0) it.getString(numIdx) ?: "" else ""
                    val digits = rawNumber.filter { char -> char.isDigit() || char == '+' }

                    if (name.isNotBlank() && digits.isNotBlank()) {
                        // Avoid duplicates of exact same name and number
                        if (matches.none { m -> m.displayName.equals(name, ignoreCase = true) && m.cleanNumber == digits }) {
                            matches.add(
                                ResolvedContact(
                                    id = id,
                                    displayName = name,
                                    phoneNumber = rawNumber,
                                    cleanNumber = digits
                                )
                            )
                        }
                    }
                }
            }
        } catch (e: SecurityException) {
            Log.w("ContactResolver", "Permission missing for contacts", e)
            return ContactResolutionResult.PermissionRequired("Contacts permission is required to find '$cleanQuery'.")
        } catch (e: Exception) {
            Log.e("ContactResolver", "Failed to query contacts", e)
        } finally {
            cursor?.close()
        }

        return when {
            matches.isEmpty() -> ContactResolutionResult.NotFound(cleanQuery)
            matches.size == 1 -> ContactResolutionResult.Single(matches[0])
            else -> ContactResolutionResult.Multiple(cleanQuery, matches)
        }
    }
}
