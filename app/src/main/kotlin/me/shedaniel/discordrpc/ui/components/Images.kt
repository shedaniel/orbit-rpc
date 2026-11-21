package me.shedaniel.discordrpc.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.drawable.toBitmap
import coil3.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.shedaniel.discordrpc.core.model.DiscordUser
import me.shedaniel.discordrpc.core.model.ImageRef

/** UI-side launcher icon cache (decoupled from the catalog so lists scroll smoothly). */
object IconCache {
    private val cache = LruCache<String, ImageBitmap>(160)
    fun peek(pkg: String): ImageBitmap? = cache.get(pkg)
    suspend fun load(context: Context, pkg: String, sizePx: Int = 144): ImageBitmap? = cache.get(pkg) ?: withContext(Dispatchers.IO) {
        runCatching {
            context.packageManager.getApplicationIcon(pkg).toBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888).asImageBitmap()
        }.getOrNull()?.also { cache.put(pkg, it) }
    }
}

@Composable
fun AppIcon(packageName: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var bmp by remember(packageName) { mutableStateOf(IconCache.peek(packageName)) }
    LaunchedEffect(packageName) { if (bmp == null) bmp = IconCache.load(context, packageName) }
    val b = bmp
    if (b != null) Image(b, contentDescription = null, modifier = modifier)
    else Box(modifier.background(MaterialTheme.colorScheme.surfaceContainerHighest))
}

/** Renders an unresolved [ImageRef] for previews, without touching the network unless the URL is already public. */
@Composable
fun RefImage(ref: ImageRef?, modifier: Modifier = Modifier, previewUrl: (ImageRef) -> String? = { null }) {
    when (ref) {
        null -> Box(modifier.background(MaterialTheme.colorScheme.surfaceContainerHighest))
        is ImageRef.AppIcon -> AppIcon(ref.packageName, modifier)
        is ImageRef.Url -> AsyncImage(ref.url, null, modifier, contentScale = ContentScale.Crop)
        is ImageRef.Raw -> {
            val url = previewUrl(ref)
            if (url != null) AsyncImage(url, null, modifier, contentScale = ContentScale.Crop)
            else Box(modifier.background(MaterialTheme.colorScheme.surfaceContainerHighest))
        }
        is ImageRef.Media -> {
            val art = ref.artwork
            val bitmap = art?.bitmap
            val url = art?.url ?: previewUrl(ref)
            when {
                bitmap != null -> {
                    val img = remember(art.key) { bitmap.asImageBitmap() }
                    Image(img, null, modifier, contentScale = ContentScale.Crop)
                }
                url != null -> AsyncImage(url, null, modifier, contentScale = ContentScale.Crop)
                else -> RefImage(ref.fallback, modifier, previewUrl)
            }
        }
    }
}

@Composable
fun Avatar(user: DiscordUser?, modifier: Modifier = Modifier) {
    val url = user?.avatarUrl(128)
    Box(modifier.background(MaterialTheme.colorScheme.secondaryContainer), contentAlignment = Alignment.Center) {
        if (url != null) AsyncImage(url, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Icon(Icons.Rounded.Person, null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.fillMaxSize(0.5f))
    }
}

