package com.mavvmusic.core.data.network

import org.schabi.newpipe.extractor.NewPipe
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NewPipeInitializer @Inject constructor(
    private val downloader: NewPipeOkHttpDownloader,
) {
    @Synchronized
    fun initialize() {
        if (NewPipe.getDownloader() == null) {
            NewPipe.init(downloader)
        }
    }
}
