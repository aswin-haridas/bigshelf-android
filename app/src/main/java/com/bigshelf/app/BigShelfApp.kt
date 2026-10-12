package com.bigshelf.app

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import com.bigshelf.app.data.Api
import com.bigshelf.app.data.Backup
import com.bigshelf.app.data.ViewSettings

class BigShelfApp : Application(), SingletonImageLoader.Factory {
    override fun onCreate() {
        super.onCreate()
        api = Api(this, BuildConfig.API_URL)
        settings = ViewSettings(this)
        backup = Backup(this)
    }

    // Images go through the API client so media served by the backend gets the auth header;
    // presigned storage URLs are on another host and are sent without it.
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components { add(OkHttpNetworkFetcherFactory(callFactory = { api.client })) }
            // Tiles fade in like the web's `starting:opacity-0 duration-300`
            .crossfade(300)
            .build()

    companion object {
        lateinit var api: Api
            private set
        lateinit var settings: ViewSettings
            private set
        lateinit var backup: Backup
            private set
    }
}
