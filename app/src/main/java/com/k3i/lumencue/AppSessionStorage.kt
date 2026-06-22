package com.k3i.lumencue

import android.content.Context

data class SavedConcertSession(
    val screen: AppScreen,
    val state: ConcertState
)

private const val SESSION_PREFS = "concert_session"
private const val KEY_EVENT_ID = "event_id"
private const val KEY_SCREEN = "screen"
private const val KEY_TRACK_INDEX = "track_index"
private const val KEY_ELAPSED_SECONDS = "elapsed_seconds"
private const val KEY_FAN_ENERGY = "fan_energy"
private const val KEY_TOTAL_REACTIONS = "total_reactions"
private const val KEY_PEAK_ENERGY = "peak_energy"
private const val KEY_COMPLETED_TRACKS = "completed_tracks"
private const val KEY_INTERACTION_EVENT_COUNTS = "interaction_event_counts"
private const val KEY_LAST_INTERACTION_EVENT_ID = "last_interaction_event_id"
private const val KEY_LANGUAGE = "language"
private const val KEY_TRANSLATION_TARGET_LANGUAGE = "translation_target_language"

fun loadConcertSession(context: Context): SavedConcertSession {
    val prefs = context.getSharedPreferences(SESSION_PREFS, Context.MODE_PRIVATE)
    val eventId = prefs.getString(KEY_EVENT_ID, sampleConcertEvents.first().id)
    val event = sampleConcertEvents.firstOrNull { it.id == eventId } ?: sampleConcertEvents.first()
    val trackIndex = prefs.getInt(KEY_TRACK_INDEX, 0).coerceIn(0, event.tracks.lastIndex)
    val screen = prefs.getString(KEY_SCREEN, AppScreen.Home.name)
        ?.let { runCatching { AppScreen.valueOf(it) }.getOrNull() }
        ?: AppScreen.Home

    val restoredScreen = when (screen) {
        AppScreen.Settings,
        AppScreen.SettingsConcert,
        AppScreen.SettingsLanguage,
        AppScreen.SettingsOperations,
        AppScreen.SettingsTechnical -> AppScreen.Settings
        else -> AppScreen.Home
    }

    return SavedConcertSession(
        screen = restoredScreen,
        state = ConcertState(
            event = event,
            trackIndex = trackIndex,
            elapsedSeconds = prefs.getInt(KEY_ELAPSED_SECONDS, 0)
                .coerceIn(0, event.tracks[trackIndex].durationSeconds - 1),
            fanEnergy = prefs.getInt(KEY_FAN_ENERGY, 42).coerceIn(20, 100),
            totalReactions = prefs.getInt(KEY_TOTAL_REACTIONS, 0).coerceAtLeast(0),
            peakEnergy = prefs.getInt(KEY_PEAK_ENERGY, 42).coerceIn(20, 100),
            completedTracks = prefs.getInt(KEY_COMPLETED_TRACKS, 0).coerceAtLeast(0),
            interactionEventParticipationCounts = deserializeInteractionEventCounts(
                prefs.getString(KEY_INTERACTION_EVENT_COUNTS, null).orEmpty()
            ),
            lastInteractionEventId = prefs.getString(KEY_LAST_INTERACTION_EVENT_ID, null)
        )
    )
}

fun saveConcertSession(context: Context, screen: AppScreen, state: ConcertState) {
    context.getSharedPreferences(SESSION_PREFS, Context.MODE_PRIVATE)
        .edit()
        .putString(KEY_EVENT_ID, state.event.id)
        .putString(KEY_SCREEN, screen.name)
        .putInt(KEY_TRACK_INDEX, state.trackIndex)
        .putInt(KEY_ELAPSED_SECONDS, state.elapsedSeconds)
        .putInt(KEY_FAN_ENERGY, state.fanEnergy)
        .putInt(KEY_TOTAL_REACTIONS, state.totalReactions)
        .putInt(KEY_PEAK_ENERGY, state.peakEnergy)
        .putInt(KEY_COMPLETED_TRACKS, state.completedTracks)
        .putString(KEY_INTERACTION_EVENT_COUNTS, serializeInteractionEventCounts(state.interactionEventParticipationCounts))
        .putString(KEY_LAST_INTERACTION_EVENT_ID, state.lastInteractionEventId)
        .apply()
}

fun loadAppLanguage(context: Context): AppLanguage {
    val prefs = context.getSharedPreferences(SESSION_PREFS, Context.MODE_PRIVATE)
    return prefs.getString(KEY_LANGUAGE, AppLanguage.Korean.name)
        ?.let { runCatching { AppLanguage.valueOf(it) }.getOrNull() }
        ?: AppLanguage.Korean
}

fun saveAppLanguage(context: Context, language: AppLanguage) {
    context.getSharedPreferences(SESSION_PREFS, Context.MODE_PRIVATE)
        .edit()
        .putString(KEY_LANGUAGE, language.name)
        .apply()
}

fun loadTranslationTargetLanguage(context: Context): TranslationTargetLanguage {
    val prefs = context.getSharedPreferences(SESSION_PREFS, Context.MODE_PRIVATE)
    return prefs.getString(KEY_TRANSLATION_TARGET_LANGUAGE, TranslationTargetLanguage.Korean.name)
        ?.let { runCatching { TranslationTargetLanguage.valueOf(it) }.getOrNull() }
        ?: TranslationTargetLanguage.Korean
}

fun saveTranslationTargetLanguage(context: Context, language: TranslationTargetLanguage) {
    context.getSharedPreferences(SESSION_PREFS, Context.MODE_PRIVATE)
        .edit()
        .putString(KEY_TRANSLATION_TARGET_LANGUAGE, language.name)
        .apply()
}

fun serializeInteractionEventCounts(counts: Map<String, Int>): String =
    counts.entries
        .filter { it.key.isNotBlank() && it.value > 0 }
        .sortedBy { it.key }
        .joinToString(separator = "\n") { "${it.key}\t${it.value}" }

fun deserializeInteractionEventCounts(raw: String): Map<String, Int> =
    raw.lineSequence()
        .mapNotNull { line ->
            val parts = line.split('\t')
            if (parts.size != 2) return@mapNotNull null
            val count = parts[1].toIntOrNull()?.takeIf { it > 0 } ?: return@mapNotNull null
            parts[0].takeIf { it.isNotBlank() }?.let { it to count }
        }
        .toMap()
