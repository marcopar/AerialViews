package com.codingbuffalo.aerialdream.services

import android.content.Context
import android.net.Uri
import com.codingbuffalo.aerialdream.models.VideoPlaylist
import com.codingbuffalo.aerialdream.models.prefs.AppleVideoPrefs
import com.codingbuffalo.aerialdream.models.prefs.GeneralPrefs
import com.codingbuffalo.aerialdream.models.prefs.LocalVideoPrefs
import com.codingbuffalo.aerialdream.models.videos.AerialVideo
import com.codingbuffalo.aerialdream.providers.AppleVideoProvider
import com.codingbuffalo.aerialdream.providers.LocalVideoProvider
import com.codingbuffalo.aerialdream.providers.VideoProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.*

class VideoService(context: Context) {
    private val providers = mutableListOf<VideoProvider>()

    init {
        if (AppleVideoPrefs.enabled)
            providers.add(AppleVideoProvider(context, AppleVideoPrefs))

        if (LocalVideoPrefs.enabled)
            providers.add(LocalVideoProvider(context, LocalVideoPrefs))
    }

    suspend fun fetchVideos(): VideoPlaylist = withContext(Dispatchers.IO) {
        val videos = mutableListOf<AerialVideo>()

        providers.forEach {
            videos.addAll(it.fetchVideos())
        }

        if (videos.isEmpty()) {
            videos.add(AerialVideo(Uri.parse(""), ""))
        }

        if (GeneralPrefs.removeDuplicates) {
            // Remove duplicates based on full path
            videos.distinctBy { it.uri.toString().lowercase() }

            // Remove duplicates based on filename only
            videos.distinctBy { it.uri.lastPathSegment?.lowercase() }
        }

        if (GeneralPrefs.shuffleVideos)
            videos.shuffle()

        VideoPlaylist(videos)
    }
}