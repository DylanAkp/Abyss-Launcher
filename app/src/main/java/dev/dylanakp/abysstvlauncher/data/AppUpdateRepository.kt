package dev.dylanakp.abysstvlauncher.data

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import dev.dylanakp.abysstvlauncher.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

data class AppUpdate(
    val versionName: String,
    val versionCode: Int,
    val releaseNotes: String,
    val apkUrl: String,
    val apkSize: Long
)

@Serializable
private data class GitHubReleaseDto(
    @SerialName("tag_name") val tagName: String = "",
    val body: String = "",
    val assets: List<GitHubAssetDto> = emptyList()
)

@Serializable
private data class GitHubAssetDto(
    val name: String = "",
    @SerialName("browser_download_url") val downloadUrl: String = "",
    val size: Long = 0L
)

object AppUpdateRepository {

    private const val REPO_OWNER = "DylanAkp"
    private const val REPO_NAME = "Abyss-Launcher"
    private const val LATEST_RELEASE_URL = "https://api.github.com/repos/$REPO_OWNER/$REPO_NAME/releases/latest"

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    fun parseVersionCode(version: String): Int {
        val clean = version.trim().removePrefix("v").removePrefix("V")
        val parts = clean.split(".")
        return runCatching {
            when (parts.size) {
                3 -> parts[0].toInt() * 10000 + parts[1].toInt() * 100 + parts[2].toInt()
                2 -> parts[0].toInt() * 10000 + parts[1].toInt() * 100
                1 -> parts[0].toInt()
                else -> 0
            }
        }.getOrDefault(0)
    }

    suspend fun checkLatestRelease(): Result<AppUpdate?> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url(LATEST_RELEASE_URL)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "Abyss-TV-Launcher")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                if (response.code == 404) return@runCatching null
                throw IOException("GitHub API error: ${response.code}")
            }

            val body = response.body?.string().orEmpty()
            if (body.isBlank()) return@runCatching null

            val release = json.decodeFromString<GitHubReleaseDto>(body)
            val tagName = release.tagName.trim()
            if (tagName.isBlank()) return@runCatching null

            val cleanTag = tagName.removePrefix("v").removePrefix("V")
            val latestVersionCode = parseVersionCode(cleanTag)
            val currentVersionCode = BuildConfig.VERSION_CODE

            val isNewer = if (latestVersionCode > 0 && currentVersionCode > 0) {
                latestVersionCode > currentVersionCode
            } else {
                cleanTag != BuildConfig.VERSION_NAME
            }

            if (!isNewer) return@runCatching null

            val apkAsset = release.assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }
                ?: return@runCatching null
            if (apkAsset.downloadUrl.isBlank()) return@runCatching null

            AppUpdate(
                versionName = cleanTag,
                versionCode = latestVersionCode,
                releaseNotes = release.body.trim(),
                apkUrl = apkAsset.downloadUrl,
                apkSize = apkAsset.size
            )
        }
    }

    suspend fun downloadApk(
        context: Context,
        apkUrl: String,
        onProgress: (Float) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url(apkUrl)
                .header("User-Agent", "Abyss-TV-Launcher")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) throw IOException("Failed to download APK: ${response.code}")

            val body = response.body ?: throw IOException("Empty response body")
            val totalBytes = body.contentLength()
            val apkFile = File(context.cacheDir, "abyss_update.apk")
            if (apkFile.exists()) apkFile.delete()

            body.byteStream().use { input ->
                FileOutputStream(apkFile).use { output ->
                    val buffer = ByteArray(8 * 1024)
                    var read: Int
                    var bytesRead = 0L
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        bytesRead += read
                        if (totalBytes > 0) {
                            onProgress(bytesRead.toFloat() / totalBytes.toFloat())
                        }
                    }
                    output.flush()
                }
            }
            apkFile
        }
    }

    fun triggerInstall(context: Context, apkFile: File): Result<Unit> {
        return runCatching {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                if (!context.packageManager.canRequestPackageInstalls()) {
                    val settingsIntent = Intent(
                        android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        android.net.Uri.parse("package:${context.packageName}")
                    ).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(settingsIntent)
                }
            }

            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                clipData = android.content.ClipData.newRawUri("", uri)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(intent)
        }
    }
}
