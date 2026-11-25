package app.mcleaner.update

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import app.mcleaner.core.update.ApkTrust
import app.mcleaner.core.update.ApkVerdict
import java.io.File

/** Checks that an APK file is MCleaner signed with the same certificates as the installed app. */
class ApkVerifier(private val context: Context) {
    fun verdict(apk: File): ApkVerdict {
        val pm = context.packageManager
        val (apkPackage, apkCerts) = read { flags -> pm.getPackageArchiveInfo(apk.path, flags) }
        val (_, ownCerts) = read { flags -> pm.getPackageInfo(context.packageName, flags) }
        return ApkTrust.verdict(apkPackage, apkCerts, context.packageName, ownCerts)
    }

    /** GET_SIGNING_CERTIFICATES first; Android 9 may leave signingInfo empty for archives, then GET_SIGNATURES. */
    @Suppress("DEPRECATION")
    private fun read(info: (Int) -> PackageInfo?): Pair<String?, List<ByteArray>> {
        val modern = runCatching { info(PackageManager.GET_SIGNING_CERTIFICATES) }.getOrNull()
        val modernCerts = modern?.signingInfo?.apkContentsSigners?.map { it.toByteArray() }.orEmpty()
        if (modern != null && modernCerts.isNotEmpty()) return modern.packageName to modernCerts
        val legacy = runCatching { info(PackageManager.GET_SIGNATURES) }.getOrNull()
        return (legacy?.packageName ?: modern?.packageName) to legacy?.signatures?.map { it.toByteArray() }.orEmpty()
    }
}
