package com.gdmusic.player

import android.app.Application
import com.gdmusic.player.data.Store

class GdApp : Application() {

    override fun onCreate() {
        super.onCreate()
        instance = this
        Store.init(this)
    }

    companion object {
        lateinit var instance: GdApp
            private set
    }
}
