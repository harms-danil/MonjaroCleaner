package app.mcleaner.data

import android.app.AppOpsManager
import android.app.WallpaperManager
import android.app.usage.StorageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.os.Process
import android.provider.Settings
import app.mcleaner.core.model.AppInfo
import app.mcleaner.core.parse.DumpsysMeminfoParser
import app.mcleaner.core.parse.CpuInfoParser
import app.mcleaner.core.parse.DumpsysStateParsers
import app.mcleaner.core.parse.ExternalCacheParser
import app.mcleaner.core.policy.InstalledApp
import app.mcleaner.core.policy.ProtectedPackages
import app.mcleaner.privileged.Privileged
import app.mcleaner.privileged.shellQuote
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AppsRepository(private val context: Context, private val privileged: Privileged) {

    private val pm: PackageManager = context.packageManager

    /** Lightweight list (labels only, no memory/cache stats) for pickers. */
    suspend fun installedApps(): List<InstalledApp> = withContext(Dispatchers.IO) {
        @Suppress("DEPRECATION")
        pm.getInstalledApplications(PackageManager.MATCH_DISABLED_COMPONENTS).map { ai ->
            InstalledApp(
                packageName = ai.packageName,
                label = runCatching { ai.loadLabel(pm).toString() }.getOrDefault(ai.packageName),
                isSystem = ai.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0,
            )
        }
    }

    suspend fun loadApps(): List<AppInfo> = withContext(Dispatchers.IO) {
        @Suppress("DEPRECATION")
        val installed = pm.getInstalledApplications(PackageManager.MATCH_DISABLED_COMPONENTS)
        val packages = installed.map { it.packageName }.toSet()
        val pss: Map<String, Long>? = if (privileged.isAvailable) {
            val r = privileged.exec("dumpsys meminfo")
            if (r.ok) DumpsysMeminfoParser.parse(r.out, packages).associate { it.packageName to it.pssKb } else null
        } else {
            null
        }
        val external: Map<String, Long> = if (privileged.isAvailable) {
            val root = Environment.getExternalStorageDirectory().path
            ExternalCacheParser.parse(privileged.exec("du -sk ${shellQuote(root)}/Android/data/*/cache 2>/dev/null").out)
        } else {
            emptyMap()
        }
        val cpu: Map<String, Double>? = if (privileged.isAvailable) {
            val r = privileged.exec("dumpsys cpuinfo")
            if (r.ok) CpuInfoParser.perPackage(r.out, packages) else null
        } else {
            null
        }
        val stats = context.getSystemService(StorageStatsManager::class.java)
        val user = Process.myUserHandle()
        val canStats = hasUsageAccess()
        installed.map { ai ->
            val cache = if (canStats) {
                runCatching { stats.queryStatsForPackage(ai.storageUuid, ai.packageName, user).cacheBytes }.getOrNull()
            } else {
                null
            }
            AppInfo(
                packageName = ai.packageName,
                label = runCatching { ai.loadLabel(pm).toString() }.getOrDefault(ai.packageName),
                isSystem = ai.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0,
                isRunning = pss?.containsKey(ai.packageName) ?: false,
                pssKb = pss?.get(ai.packageName),
                cacheBytes = ExternalCacheParser.combine(cache, external[ai.packageName]),
                isFrozen = !ai.enabled,
                cpuPercent = cpu?.let { it[ai.packageName] ?: 0.0 },
            )
        }
    }

    /** Sum of all apps' cache, without the expensive dumpsys. Zero without usage access. */
    suspend fun totalCacheBytes(): Long = withContext(Dispatchers.IO) {
        if (!hasUsageAccess()) return@withContext 0L
        val stats = context.getSystemService(StorageStatsManager::class.java)
        val user = Process.myUserHandle()
        @Suppress("DEPRECATION")
        pm.getInstalledApplications(0).sumOf { ai ->
            runCatching { stats.queryStatsForPackage(ai.storageUuid, ai.packageName, user).cacheBytes }.getOrDefault(0L)
        }
    }

    data class BusyProbe(val packages: Set<String>, val known: Boolean)

    /**
     * Apps that must not be stopped right now: foreground services, playing media, resumed on any display.
     * Output is filtered on the shell side to stay far below the binder limit. If any probe fails or
     * doesn't look like real dumpsys output, [BusyProbe.known] is false and nothing gets force-stopped.
     */
    suspend fun busyPackages(): BusyProbe {
        if (!privileged.isAvailable) return BusyProbe(emptySet(), known = false)
        val services = privileged.exec(
            "dumpsys activity services | grep -E '^[[:space:]]*(\\* ServiceRecord|packageName=|isForeground=)'",
        )
        val media = privileged.exec("dumpsys media_session")
        val activities = privileged.exec(
            "dumpsys activity activities | grep -E 'ACTIVITY MANAGER ACTIVITIES|ResumedActivity:'",
        )
        val known = services.ok && DumpsysStateParsers.looksLikeServices(services.out) &&
            media.ok && DumpsysStateParsers.looksLikeMediaSessions(media.out) &&
            activities.ok && DumpsysStateParsers.looksLikeActivities(activities.out)
        val packages = buildSet {
            addAll(DumpsysStateParsers.foregroundServicePackages(services.out))
            // Every session, not only playing: a paused player keeps its queue and position.
            addAll(ProtectedPackages.mediaSessionPackages(media.out))
            addAll(DumpsysStateParsers.resumedPackages(activities.out))
        }
        return BusyProbe(packages, known)
    }

    /**
     * Apps the car needs to stay usable, in any mode: enabled accessibility services and notification
     * listeners, the live wallpaper, and every launcher (HOME handler).
     */
    suspend fun carUsabilityPackages(): Set<String> = withContext(Dispatchers.IO) {
        buildSet {
            addAll(ProtectedPackages.fromComponentList(secureSetting(Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)))
            addAll(ProtectedPackages.fromComponentList(secureSetting(ENABLED_NOTIFICATION_LISTENERS)))
            runCatching { WallpaperManager.getInstance(context).wallpaperInfo?.packageName }.getOrNull()?.let(::add)
            addAll(homePackages())
        }
    }

    /** Must never be frozen: every launcher and the current keyboard. */
    suspend fun freezeProtectedPackages(): Set<String> = withContext(Dispatchers.IO) {
        homePackages() + setOfNotNull(imePackage())
    }

    /** Every app that handles HOME, not only the default: the resolved default may be the "android" chooser. */
    fun homePackages(): Set<String> {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val all = runCatching {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(home, PackageManager.MATCH_DISABLED_COMPONENTS).mapNotNull { it.activityInfo?.packageName }
        }.getOrDefault(emptyList())
        return (all + listOfNotNull(launcherPackage())).toSet()
    }

    /** Secure settings via the resolver; via shell when the resolver hides them from apps. */
    private suspend fun secureSetting(key: String): String? {
        val direct = runCatching { Settings.Secure.getString(context.contentResolver, key) }.getOrNull()
        if (direct != null || !privileged.isAvailable) return direct
        val r = privileged.exec("settings get secure ${shellQuote(key)}")
        return if (r.ok) r.out.trim() else null
    }

    suspend fun currentUser(): Int {
        if (!privileged.isAvailable) return Process.myUid() / PER_USER_RANGE
        val r = privileged.exec("am get-current-user")
        return if (r.ok) DumpsysStateParsers.currentUser(r.out) else 0
    }

    fun launcherPackage(): String? = runCatching {
        pm.resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo?.packageName
    }.getOrNull()

    fun imePackage(): String? =
        Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)?.substringBefore('/')

    @Suppress("DEPRECATION")
    fun hasUsageAccess(): Boolean {
        val ops = context.getSystemService(AppOpsManager::class.java)
        val mode = if (Build.VERSION.SDK_INT >= 29) {
            ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        } else {
            @Suppress("DEPRECATION")
            ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun hasAllFilesAccess(): Boolean =
        if (Build.VERSION.SDK_INT >= 30) {
            Environment.isExternalStorageManager()
        } else {
            context.checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED &&
                context.checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }

    private companion object {
        const val PER_USER_RANGE = 100_000

        /** Settings.Secure.ENABLED_NOTIFICATION_LISTENERS is @hide. */
        const val ENABLED_NOTIFICATION_LISTENERS = "enabled_notification_listeners"
    }

    /** In full mode grant ourselves usage stats and all-files access, so the user doesn't have to. */
    suspend fun ensureGrants() {
        if (!privileged.isAvailable) return
        val me = shellQuote(context.packageName)
        if (!hasUsageAccess()) privileged.exec("appops set $me GET_USAGE_STATS allow")
        if (!hasAllFilesAccess()) {
            if (Build.VERSION.SDK_INT >= 30) {
                privileged.exec("appops set $me MANAGE_EXTERNAL_STORAGE allow")
            } else {
                // Android 9/10: shared storage opens up only with READ_EXTERNAL_STORAGE, WRITE alone is not enough.
                privileged.exec("pm grant $me android.permission.READ_EXTERNAL_STORAGE")
                privileged.exec("pm grant $me android.permission.WRITE_EXTERNAL_STORAGE")
            }
        }
    }

    fun canInstallApps(): Boolean = context.packageManager.canRequestPackageInstalls()

    fun ignoresBatteryOptimizations(): Boolean =
        context.getSystemService(android.os.PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)

    /** «Выдать все» with Shizuku: everything [ensureGrants] does plus app installs (updates) and no battery limits. */
    suspend fun grantAll() {
        if (!privileged.isAvailable) return
        ensureGrants()
        val me = shellQuote(context.packageName)
        if (!canInstallApps()) privileged.exec("appops set $me REQUEST_INSTALL_PACKAGES allow")
        if (!ignoresBatteryOptimizations()) privileged.exec("dumpsys deviceidle whitelist ${shellQuote("+" + context.packageName)}")
    }
}
