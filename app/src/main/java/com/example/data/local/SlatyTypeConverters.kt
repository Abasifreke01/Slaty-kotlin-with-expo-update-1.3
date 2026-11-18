package com.example.data.local

import androidx.room.TypeConverter
import org.json.JSONArray

class SlatyTypeConverters {

    @TypeConverter
    fun fromStringList(list: List<String>?): String {
        if (list == null || list.isEmpty()) return ""
        val jsonArray = JSONArray()
        list.forEach { jsonArray.put(it) }
        return jsonArray.toString()
    }

    @TypeConverter
    fun toStringList(data: String?): List<String> {
        if (data.isNullOrBlank()) return emptyList()
        return try {
            val jsonArray = JSONArray(data)
            val list = mutableListOf<String>()
            for (i in 0 until jsonArray.length()) {
                list.add(jsonArray.getString(i))
            }
            list
        } catch (e: Exception) {
            // Fallback to comma-separated parsing if legacy format
            data.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        }
    }
}
