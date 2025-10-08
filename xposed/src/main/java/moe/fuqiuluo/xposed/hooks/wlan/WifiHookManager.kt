package moe.fuqiuluo.xposed.hooks.wlan

import moe.fuqiuluo.xposed.utils.Logger

/**
 * WiFi Hook管理器
 * 用于管理地图应用WiFi数据拦截的配置和状态
 */
object WifiHookManager {

    // WiFi Hook是否启用
    var isWifiHookEnabled: Boolean = true
        set(value) {
            field = value
            Logger.info("WiFi Hook状态已${if (value) "启用" else "禁用"}")
        }

    // 是否启用详细日志
    var isDetailedLogEnabled: Boolean = false

    // 白名单应用包名（这些应用不会被Hook）
    private val whitelistPackages = mutableSetOf<String>()

    // 黑名单应用包名（强制Hook这些应用）
    private val blacklistPackages = mutableSetOf(
        "com.baidu.BaiduMap",
        "com.autonavi.minimap",
        "com.tencent.map",
        "com.google.android.apps.maps"
    )

    /**
     * 添加应用到白名单
     */
    fun addToWhitelist(packageName: String) {
        whitelistPackages.add(packageName)
        Logger.info("已将 $packageName 添加到WiFi Hook白名单")
    }

    /**
     * 从白名单移除应用
     */
    fun removeFromWhitelist(packageName: String) {
        whitelistPackages.remove(packageName)
        Logger.info("已将 $packageName 从WiFi Hook白名单移除")
    }

    /**
     * 添加应用到黑名单
     */
    fun addToBlacklist(packageName: String) {
        blacklistPackages.add(packageName)
        Logger.info("已将 $packageName 添加到WiFi Hook黑名单")
    }

    /**
     * 从黑名单移除应用
     */
    fun removeFromBlacklist(packageName: String) {
        blacklistPackages.remove(packageName)
        Logger.info("已将 $packageName 从WiFi Hook黑名单移除")
    }

    /**
     * 判断是否应该Hook指定应用
     */
    fun shouldHookPackage(packageName: String): Boolean {
        // 如果WiFi Hook未启用，则不Hook任何应用
        if (!isWifiHookEnabled) {
            return false
        }

        // 白名单中的应用不Hook
        if (whitelistPackages.contains(packageName)) {
            return false
        }

        // 黑名单中的应用强制Hook
        if (blacklistPackages.contains(packageName)) {
            return true
        }

        // 检查包名是否包含地图相关关键词
        val mapKeywords = listOf("map", "location", "gps", "navigation", "baidu", "amap", "tencent")
        return mapKeywords.any { packageName.contains(it, ignoreCase = true) }
    }

    /**
     * 获取当前白名单
     */
    fun getWhitelist(): Set<String> = whitelistPackages.toSet()

    /**
     * 获取当前黑名单
     */
    fun getBlacklist(): Set<String> = blacklistPackages.toSet()

    /**
     * 重置所有配置
     */
    fun reset() {
        whitelistPackages.clear()
        blacklistPackages.clear()
        blacklistPackages.addAll(setOf(
            "com.baidu.BaiduMap",
            "com.autonavi.minimap",
            "com.tencent.map",
            "com.google.android.apps.maps"
        ))
        isWifiHookEnabled = true
        isDetailedLogEnabled = false
        Logger.info("WiFi Hook配置已重置")
    }
}
