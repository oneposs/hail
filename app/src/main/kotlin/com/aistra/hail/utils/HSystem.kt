package com.aistra.hail.utils

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import androidx.core.content.getSystemService
import androidx.core.os.UserManagerCompat.isUserUnlocked
import com.aistra.hail.app.HailData

object HSystem {
    fun isInteractive(context: Context): Boolean {
        val powerManger = context.getSystemService<PowerManager>()!!
        return powerManger.isInteractive
    }

    fun isCharging(context: Context): Boolean {
        val batteryStatus = IntentFilter(Intent.ACTION_BATTERY_CHANGED).let { filter ->
            context.registerReceiver(null, filter)
        }
        val status = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        return status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
    }

    @Suppress("SameParameterValue")
    private fun checkOp(context: Context, op: String): Boolean {
        val opsManager = context.getSystemService<AppOpsManager>()!!
        val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            opsManager.unsafeCheckOp(op, android.os.Process.myUid(), context.packageName)
        } else {
            @Suppress("DEPRECATION")
            opsManager.checkOp(op, android.os.Process.myUid(), context.packageName)
        }
        return result == AppOpsManager.MODE_ALLOWED
    }

    fun checkOpUsageStats(context: Context): Boolean =
        checkOp(context, AppOpsManager.OPSTR_GET_USAGE_STATS)

    fun isForegroundApp(context: Context, packageName: String): Boolean {
        if (!isUserUnlocked(context))
            return false  // Starting from Android R, usage stats can't be read before unlocked
        val usageStatsManager = context.getSystemService<UsageStatsManager>()!!
        val now = System.currentTimeMillis()
        var stats = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_BEST,
            now - 1000 * 60 * (HailData.autoFreezeDelay + 1), now  // to ensure that we can get the last app used
        )
        if (stats.isEmpty())
            return false  // this should not happen...
        stats = stats.sortedBy { it.lastTimeUsed }  // if stats is empty this would throw an error and crash
        val foregroundPackageName = stats.last()?.packageName
        return foregroundPackageName == packageName
    }
}