package com.gdmusic.player.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.google.gson.Gson
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

object Store {

    private const val PREFS_NAME = "gd_player"
    private const val KEY_STATE = "library_state"
    private const val MAX_HISTORY = 200

    private val gson = Gson()
    private lateinit var prefs: SharedPreferences
    private val _state = MutableStateFlow(LibraryState())
    val state: StateFlow<LibraryState> = _state.asStateFlow()

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _state.value = load()
    }

    private fun load(): LibraryState = try {
        val raw = prefs.getString(KEY_STATE, null)
        if (raw.isNullOrBlank()) LibraryState()
        else gson.fromJson(raw, LibraryState::class.java) ?: LibraryState()
    } catch (e: Exception) {
        LibraryState()
    }

    private fun commit(state: LibraryState) {
        _state.value = state
        try {
            prefs.edit { putString(KEY_STATE, gson.toJson(state)) }
        } catch (e: Exception) {
        }
    }

    fun isFavorite(song: Song): Boolean =
        _state.value.favorites.any { it.key == song.key }

    fun toggleFavorite(song: Song) {
        val current = _state.value
        val exists = current.favorites.any { it.key == song.key }
        val favorites = if (exists) {
            current.favorites.filterNot { it.key == song.key }
        } else {
            listOf(song) + current.favorites
        }
        commit(current.copy(favorites = favorites))
    }

    fun addHistory(song: Song) {
        if (song.key.isBlank()) return
        val current = _state.value
        val history = (listOf(song) + current.history.filterNot { it.key == song.key })
            .take(MAX_HISTORY)
        commit(current.copy(history = history))
    }

    fun clearHistory() {
        commit(_state.value.copy(history = emptyList()))
    }

    fun saveSettings(settings: Settings) {
        commit(_state.value.copy(settings = settings))
    }

    fun createPlaylist(name: String): UserPlaylist {
        val playlist = UserPlaylist(id = UUID.randomUUID().toString(), name = name)
        val current = _state.value
        commit(current.copy(playlists = current.playlists + playlist))
        return playlist
    }

    fun deletePlaylist(id: String) {
        val current = _state.value
        commit(current.copy(playlists = current.playlists.filterNot { it.id == id }))
    }

    fun renamePlaylist(id: String, name: String) {
        val current = _state.value
        commit(
            current.copy(
                playlists = current.playlists.map {
                    if (it.id == id) it.copy(name = name) else it
                }
            )
        )
    }

    fun addToPlaylist(id: String, song: Song) {
        val current = _state.value
        commit(
            current.copy(
                playlists = current.playlists.map { playlist ->
                    if (playlist.id != id) playlist
                    else if (playlist.songs.any { it.key == song.key }) playlist
                    else playlist.copy(songs = playlist.songs + song)
                }
            )
        )
    }

    fun removeFromPlaylist(id: String, songKey: String) {
        val current = _state.value
        commit(
            current.copy(
                playlists = current.playlists.map { playlist ->
                    if (playlist.id != id) playlist
                    else playlist.copy(songs = playlist.songs.filterNot { it.key == songKey })
                }
            )
        )
    }

    fun setQueue(songs: List<Song>, index: Int) {
        commit(_state.value.copy(queue = songs, queueIndex = index.coerceAtLeast(0)))
    }

    fun setQueueIndex(index: Int) {
        commit(_state.value.copy(queueIndex = index.coerceAtLeast(0)))
    }
}
