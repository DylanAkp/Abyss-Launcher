package dev.dylanakp.abysstvlauncher.ui.components

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.size.Precision
import dev.dylanakp.abysstvlauncher.theme.AbyssVoid
import kotlinx.coroutines.Dispatchers
import okhttp3.Dispatcher
import okhttp3.OkHttpClient

object AbyssImagePipeline {
    private var instance: ImageLoader? = null

    fun getLoader(context: Context): ImageLoader {
        return instance ?: synchronized(this) {
            instance ?: run {
                val okHttpClient = OkHttpClient.Builder()
                    .dns(dev.dylanakp.abysstvlauncher.data.PreferIpv4Dns)
                    .dispatcher(Dispatcher().apply {
                        maxRequests = 16
                        maxRequestsPerHost = 6
                    })
                    .build()

                ImageLoader.Builder(context.applicationContext)
                    .okHttpClient(okHttpClient)
                    .memoryCache {
                        MemoryCache.Builder(context.applicationContext)
                            .maxSizePercent(0.25)
                            .strongReferencesEnabled(true)
                            .build()
                    }
                    .diskCache {
                        DiskCache.Builder()
                            .directory(context.applicationContext.cacheDir.resolve("abyss_image_cache"))
                            .maxSizeBytes(250L * 1024L * 1024L) // 250MB persistent disk cache
                            .build()
                    }
                    .interceptorDispatcher(Dispatchers.IO)
                    .transformationDispatcher(Dispatchers.IO)
                    .decoderDispatcher(Dispatchers.IO.limitedParallelism(2))
                    .respectCacheHeaders(false)
                    .networkCachePolicy(CachePolicy.ENABLED)
                    .diskCachePolicy(CachePolicy.ENABLED)
                    .memoryCachePolicy(CachePolicy.ENABLED)
                    .allowHardware(true)
                    .bitmapConfig(Bitmap.Config.ARGB_8888)
                    .crossfade(false)
                    .build()
                    .also { instance = it }
            }
        }
    }
}

@Composable
fun AbyssMediaArtwork(
    imageUrl: String,
    modifier: Modifier = Modifier,
    primaryColor: Color = Color(0xFF1E293B),
    secondaryColor: Color = Color(0xFF0F172A),
    darkColor: Color = AbyssVoid,
    seedKey: String = "",
    isHeroBackdrop: Boolean = false,
    targetWidth: Int = if (isHeroBackdrop) 1920 else 240,
    targetHeight: Int = if (isHeroBackdrop) 1080 else 340
) {
    if (imageUrl.isBlank()) {
        Box(modifier = modifier.background(darkColor))
        return
    }

    val context = LocalContext.current
    val imageLoader = remember(context) { AbyssImagePipeline.getLoader(context) }

    // 1. Instant 0ms synchronous memory hit (Direct Hardware Bitmap rendering in frame 0)
    val cachedBitmap = remember(imageUrl) {
        if (imageUrl.isNotBlank()) {
            imageLoader.memoryCache?.get(MemoryCache.Key(imageUrl))?.bitmap
        } else null
    }

    if (cachedBitmap != null) {
        Image(
            bitmap = cachedBitmap.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier
        )
        return
    }

    // 2. Not in RAM cache yet: AsyncImage handles disk/network fetch, caching into memoryCache, and rendering
    val request = remember(imageUrl, isHeroBackdrop, targetWidth, targetHeight) {
        ImageRequest.Builder(context)
            .data(imageUrl)
            .memoryCacheKey(imageUrl)
            .diskCacheKey(imageUrl)
            .size(targetWidth, targetHeight)
            .precision(Precision.INEXACT)
            .allowHardware(true)
            .bitmapConfig(Bitmap.Config.ARGB_8888)
            .crossfade(false)
            .setHeader("User-Agent", "AbyssTVLauncher/1.0.0 (Android TV; Jellyfin/Seer)")
            .build()
    }

    Box(modifier = modifier.background(darkColor)) {
        AsyncImage(
            model = request,
            imageLoader = imageLoader,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
    }
}
