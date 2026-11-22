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
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
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

sealed interface InstallResult {
    object Success : InstallResult
    object NeedsPermission : InstallResult
    data class Failure(val error: Throwable) : InstallResult
}

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

private data class SemVer(val core: List<Int>, val prerelease: List<String>) : Comparable<SemVer> {
    override fun compareTo(other: SemVer): Int {
        for (i in 0 until maxOf(core.size, other.core.size)) {
            val diff = core.getOrElse(i) { 0 }.compareTo(other.core.getOrElse(i) { 0 })
            if (diff != 0) return diff
        }
        if (prerelease.isEmpty() && other.prerelease.isEmpty()) return 0
        if (prerelease.isEmpty()) return 1
        if (other.prerelease.isEmpty()) return -1
        for (i in 0 until minOf(prerelease.size, other.prerelease.size)) {
            val a = prerelease[i]
            val b = other.prerelease[i]
            val aNum = a.toLongOrNull()
            val bNum = b.toLongOrNull()
            val diff = when {
                aNum != null && bNum != null -> aNum.compareTo(bNum)
                aNum != null -> -1
                bNum != null -> 1
                else -> a.compareTo(b)
            }
            if (diff != 0) return diff
        }
        return prerelease.size.compareTo(other.prerelease.size)
    }
}

object AppUpdateRepository : KoinComponent {

    private const val REPO_OWNER = "DylanAkp"
    private const val REPO_NAME = "Abyss-Launcher"
    private const val LATEST_RELEASE_URL = "https://api.github.com/repos/$REPO_OWNER/$REPO_NAME/releases/latest"
    private val APK_ASSET_NAME = Regex("^Abyss-Launcher-.+\\.apk$", RegexOption.IGNORE_CASE)

    private val json: Json by inject()
    private val baseClient: OkHttpClient by inject()
    private val client: OkHttpClient by lazy {
        baseClient.newBuilder()
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

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

    private fun parseSemVer(raw: String): SemVer? {
        val clean = raw.trim().removePrefix("v").removePrefix("V")
        val withoutBuild = clean.substringBefore("+")
        val core = withoutBuild.substringBefore("-")
        if (core.isBlank() || core.split(".").any { it.toIntOrNull() == null }) return null
        val pre = withoutBuild.substringAfter("-", "")
        return SemVer(
            core = core.split(".").map { it.toInt() },
            prerelease = if (pre.isBlank()) emptyList() else pre.split(".")
        )
    }

    private fun isNewerThanInstalled(tag: String): Boolean {
        val latest = parseSemVer(tag)
        val current = parseSemVer(BuildConfig.VERSION_NAME)
        if (latest != null && current != null) return latest > current

        val latestCode = parseVersionCode(tag)
        if (latestCode > 0 && BuildConfig.VERSION_CODE > 0) {
            return latestCode > BuildConfig.VERSION_CODE
        }
        return tag.trim().removePrefix("v").removePrefix("V") != BuildConfig.VERSION_NAME
    }

    suspend fun checkLatestRelease(): Result<AppUpdate?> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url(LATEST_RELEASE_URL)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "Abyss-TV-Launcher")
                .build()

            val release = client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    if (response.code == 404) return@runCatching null
                    throw IOException("GitHub API error: ${response.code}")
                }

                val body = response.body?.string().orEmpty()
                if (body.isBlank()) return@runCatching null

                json.decodeFromString<GitHubReleaseDto>(body)
            }
            val tagName = release.tagName.trim()
            if (tagName.isBlank()) return@runCatching null

            val cleanTag = tagName.removePrefix("v").removePrefix("V")
            if (!isNewerThanInstalled(tagName)) return@runCatching null

            val apkAsset = release.assets.firstOrNull {
                APK_ASSET_NAME.matches(it.name) && it.downloadUrl.isNotBlank()
            } ?: return@runCatching null

            AppUpdate(
                versionName = cleanTag,
                versionCode = parseVersionCode(cleanTag),
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

            val apkFile = File(context.cacheDir, "abyss_update.apk")
            if (apkFile.exists()) apkFile.delete()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("Failed to download APK: ${response.code}")

                val body = response.body ?: throw IOException("Empty response body")
                val totalBytes = body.contentLength()

                body.byteStream().use { input ->
                    FileOutputStream(apkFile).use { output ->
                        val buffer = ByteArray(8 * 1024)
                        var read: Int
                        var bytesRead = 0L
                        var lastPercent = -1
                        while (input.read(buffer).also { read = it } != -1) {
                            output.write(buffer, 0, read)
                            bytesRead += read
                            if (totalBytes > 0) {
                                val progress = bytesRead.toFloat() / totalBytes.toFloat()
                                val pct = (progress * 100).toInt()
                                if (pct != lastPercent) {
                                    lastPercent = pct
                                    onProgress(progress)
                                }
                            }
                        }
                        output.flush()
                    }
                }
            }
            if (!verifyApkSignature(context, apkFile)) {
                apkFile.delete()
                throw SecurityException("Downloaded APK signature does not match installed app")
            }
            apkFile
        }
    }

    private fun verifyApkSignature(context: Context, apkFile: File): Boolean {
        val pm = context.packageManager
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            val installedInfo = pm.getPackageInfo(
                context.packageName,
                android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES
            )
            val archiveInfo = pm.getPackageArchiveInfo(
                apkFile.absolutePath,
                android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES
            ) ?: return false
            if (archiveInfo.packageName != context.packageName) return false
            val installedSigs = installedInfo.signingInfo?.apkContentsSigners?.map { it.toCharsString() }?.toSet() ?: return false
            val archiveSigs = archiveInfo.signingInfo?.apkContentsSigners?.map { it.toCharsString() }?.toSet() ?: return false
            installedSigs.isNotEmpty() && installedSigs == archiveSigs
        } else {
            @Suppress("DEPRECATION")
            val installedInfo = pm.getPackageInfo(
                context.packageName,
                android.content.pm.PackageManager.GET_SIGNATURES
            )
            @Suppress("DEPRECATION")
            val archiveInfo = pm.getPackageArchiveInfo(
                apkFile.absolutePath,
                android.content.pm.PackageManager.GET_SIGNATURES
            ) ?: return false
            if (archiveInfo.packageName != context.packageName) return false
            @Suppress("DEPRECATION")
            val installedSigs = installedInfo.signatures?.map { it.toCharsString() }?.toSet() ?: return false
            @Suppress("DEPRECATION")
            val archiveSigs = archiveInfo.signatures?.map { it.toCharsString() }?.toSet() ?: return false
            installedSigs.isNotEmpty() && installedSigs == archiveSigs
        }
    }

    fun triggerInstall(context: Context, apkFile: File): InstallResult {
        return try {
            if (!verifyApkSignature(context, apkFile)) {
                apkFile.delete()
                throw SecurityException("APK signature verification failed")
            }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O &&
                !context.packageManager.canRequestPackageInstalls()
            ) {
                val settingsIntent = Intent(
                    android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    android.net.Uri.parse("package:${context.packageName}")
                ).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(settingsIntent)
                InstallResult.NeedsPermission
            } else {
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
                InstallResult.Success
            }
        } catch (e: Exception) {
            InstallResult.Failure(e)
        }
    }
}
