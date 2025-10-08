@file:Suppress("UNCHECKED_CAST", "PrivateApi")
package moe.fuqiuluo.xposed.hooks.wlan

import android.net.wifi.ScanResult
import android.net.wifi.WifiInfo
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import moe.fuqiuluo.xposed.utils.FakeLoc
import moe.fuqiuluo.xposed.utils.Logger
import moe.fuqiuluo.xposed.utils.beforeHook
import moe.fuqiuluo.xposed.utils.hookAllMethods

/**
 * 专门针对地图类应用的WiFi数据Hook模块
 * 用于拦截地图应用的WiFi扫描请求，返回空数据以防止位置泄露
 */
object MapAppWifiHook {

    /**
     * Hook指定应用的WiFi功能
     */
    operator fun invoke(classLoader: ClassLoader, packageName: String) {
        if (!WifiHookManager.shouldHookPackage(packageName)) {
            return
        }

        Logger.info("开始Hook地图应用WiFi功能: $packageName")

        try {
            hookWifiManager(classLoader, packageName)
            hookWifiScanner(classLoader, packageName)
            Logger.info("成功Hook应用 $packageName 的WiFi功能")
        } catch (e: Throwable) {
            Logger.error("Hook应用 $packageName 的WiFi功能失败", e)
        }
    }

    /**
     * Hook WifiManager相关方法
     */
    private fun hookWifiManager(classLoader: ClassLoader, packageName: String) {
        val wifiManagerClass = try {
            XposedHelpers.findClass("android.net.wifi.WifiManager", classLoader)
        } catch (e: Throwable) {
            Logger.warn("未找到WifiManager类: $e")
            return
        }

        // Hook getScanResults方法 - 返回空的扫描结果列表
        wifiManagerClass.hookAllMethods("getScanResults", object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                if (WifiHookManager.isDetailedLogEnabled) {
                    Logger.debug("拦截 $packageName 的WiFi扫描请求 - getScanResults")
                }

                if (FakeLoc.enableMockWifi && WifiHookManager.isWifiHookEnabled) {
                    Logger.info("为地图应用 $packageName 返回空WiFi扫描结果")
                    param.result = emptyList<ScanResult>()
                }
            }
        })

        // Hook getConnectionInfo方法 - 返回空的连接信息
        wifiManagerClass.hookAllMethods("getConnectionInfo", object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                if (WifiHookManager.isDetailedLogEnabled) {
                    Logger.debug("拦截 $packageName 的WiFi连接信息请求 - getConnectionInfo")
                }

                if (FakeLoc.enableMockWifi && WifiHookManager.isWifiHookEnabled) {
                    Logger.info("为地图应用 $packageName 返回空WiFi连接信息")

                    // 创建一个空的WifiInfo对象
                    val wifiInfo = WifiInfo::class.java.getConstructor().newInstance()
                    try {
                        XposedHelpers.callMethod(wifiInfo, "setMacAddress", "02:00:00:00:00:00")
                        XposedHelpers.callMethod(wifiInfo, "setBSSID", null)
                        XposedHelpers.callMethod(wifiInfo, "setSSID", null)
                    } catch (_: Throwable) {
                        // 忽略设置失败的情况
                    }
                    param.result = wifiInfo
                }
            }
        })

        // Hook startScan方法 - 阻止WiFi主动扫描
        wifiManagerClass.hookAllMethods("startScan", object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                if (WifiHookManager.isDetailedLogEnabled) {
                    Logger.debug("拦截 $packageName 的WiFi主动扫描请求 - startScan")
                }

                if (FakeLoc.enableMockWifi && WifiHookManager.isWifiHookEnabled) {
                    Logger.info("阻止地图应用 $packageName 的WiFi主动扫描")
                    param.result = false  // 返回false表示扫描失败
                }
            }
        })

        // Hook getConfiguredNetworks方法 - 返回空的已配置网络列表
        wifiManagerClass.hookAllMethods("getConfiguredNetworks", object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                if (WifiHookManager.isDetailedLogEnabled) {
                    Logger.debug("拦截 $packageName 的已配置WiFi网络请求 - getConfiguredNetworks")
                }

                if (FakeLoc.enableMockWifi && WifiHookManager.isWifiHookEnabled) {
                    Logger.info("为地图应用 $packageName 返回空的已配置WiFi网络列表")
                    param.result = emptyList<Any>()
                }
            }
        })
    }

    /**
     * Hook WifiScanner相关方法（适用于Android 6.0+）
     */
    private fun hookWifiScanner(classLoader: ClassLoader, packageName: String) {
        val wifiScannerClass = try {
            XposedHelpers.findClass("android.net.wifi.WifiScanner", classLoader)
        } catch (_: Throwable) {
            // WifiScanner在较老的Android版本中可能不存在
            Logger.debug("未找到WifiScanner类，可能是较老的Android版本")
            return
        }

        // Hook startScan方法
        wifiScannerClass.hookAllMethods("startScan", beforeHook {
            if (WifiHookManager.isDetailedLogEnabled) {
                Logger.debug("拦截 $packageName 的WifiScanner扫描请求")
            }

            if (FakeLoc.enableMockWifi && WifiHookManager.isWifiHookEnabled) {
                Logger.info("阻止地图应用 $packageName 的WifiScanner扫描")
                // 不执行原方法，直接返回
                result = null
            }
        })

        // Hook getSingleScanResults方法
        wifiScannerClass.hookAllMethods("getSingleScanResults", object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                if (WifiHookManager.isDetailedLogEnabled) {
                    Logger.debug("拦截 $packageName 的单次WiFi扫描结果请求")
                }

                if (FakeLoc.enableMockWifi && WifiHookManager.isWifiHookEnabled) {
                    Logger.info("为地图应用 $packageName 返回空的单次WiFi扫描结果")
                    param.result = emptyList<ScanResult>()
                }
            }
        })
    }
}
