package me.shedaniel.discordrpc

import android.app.Application
import android.content.Context
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import me.shedaniel.discordrpc.net.TrafficCategory

class App : Application(), SingletonImageLoader.Factory {
    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        graph = AppGraph(this)
        if (BuildConfig.DEBUG) android.webkit.WebView.setWebContentsDebuggingEnabled(true)
    }

    /** UI images (avatars, artwork previews) go through the metered client too, cached on disk. */
    override fun newImageLoader(context: PlatformContext): ImageLoader = ImageLoader.Builder(context)
        .components { add(OkHttpNetworkFetcherFactory(callFactory = { graph.http.client(TrafficCategory.OTHER) })) }
        .build()
}

val Context.graph: AppGraph get() = (applicationContext as App).graph
