package com.example.cobot.color_game
/**
 * This file provides functionality to persist and retrieve player scores
 * using Jetpack DataStore with JSON serialization.
 *
 * - `savePlayerScore`: Saves or updates a player's score, ensures unique names,
 *   sorts scores in descending order, and stores them as a JSON string.
 * - `getPlayerScores`: Retrieves the list of saved scores, deserializing from JSON.
 *
 * Scores are stored in shared preferences under the key "scores_json".
 */

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

val Context.dataStore by preferencesDataStore(name = "score_table")

object Keys {
    val SCORES = stringPreferencesKey("scores_json")
}

suspend fun savePlayerScore(context: Context, newScore: PlayerScore) {
    context.dataStore.edit { prefs ->
        val currentList = getPlayerScores(context).toMutableList()
        currentList.removeAll { it.name == newScore.name }
        currentList.add(newScore)
        currentList.sortByDescending { it.score }
        val json = Json.encodeToString(currentList)
        prefs[Keys.SCORES] = json
    }
}

suspend fun getPlayerScores(context: Context): List<PlayerScore> {
    val prefs = context.dataStore.data.first()
    val json = prefs[Keys.SCORES] ?: return emptyList()
    return try {
        Json.decodeFromString(json)
    } catch (e: Exception) {
        emptyList()
    }
}
