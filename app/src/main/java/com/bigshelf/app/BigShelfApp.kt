package com.bigshelf.app

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.Uri
import coil3.intercept.Interceptor
import coil3.key.Keyer
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import com.bigshelf.app.data.Api
import com.bigshelf.app.data.Backup
import com.bigshelf.app.data.Updater
import com.bigshelf.app.data.ViewSettings

class BigShelfApp : Application(), SingletonImageLoader.Factory {
    override fun onCreate() {
        super.onCreate()
        api = Api(this, BuildConfig.API_URL)
        settings = ViewSettings(this)
        backup = Backup(this)
        Updater.schedule(this)
    }

    // Images go through the API client so media served by the backend gets the auth header;
    // presigned storage URLs are on another host and are sent without it.
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components {
                add(OkHttpNetworkFetcherFactory(callFactory = { api.client }))
                // Presigned URLs get a fresh signature every ~30 min (backend presign cache), so
                // keying on the full URL misses both caches and refetches every image. Key on the
                // path instead: storage keys never get new bytes under the same name.
                add(Keyer<Uri> { data, _ -> data.toString().substringBefore('?') })
                add(Interceptor { chain ->
                    val url = chain.request.data as? String
                    if (url == null || '?' !in url) chain.proceed()
                    else chain.withRequest(chain.request.newBuilder().diskCacheKey(url.substringBefore('?')).build()).proceed()
                })
            }
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
