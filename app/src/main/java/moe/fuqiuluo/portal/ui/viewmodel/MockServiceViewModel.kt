package moe.fuqiuluo.portal.ui.viewmodel

import android.app.Activity
import android.location.LocationManager
import android.util.Log
import androidx.lifecycle.ViewModel
import com.tencent.bugly.crashreport.CrashReport
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import moe.fuqiuluo.portal.android.coro.CoroutineController
import moe.fuqiuluo.portal.android.coro.CoroutineRouteMock
import moe.fuqiuluo.portal.ext.Loc4j
import moe.fuqiuluo.portal.ext.accuracy
import moe.fuqiuluo.portal.ext.altitude
import moe.fuqiuluo.portal.ext.reportDuration
import moe.fuqiuluo.portal.ext.speed
import moe.fuqiuluo.portal.service.MockServiceHelper
import moe.fuqiuluo.portal.ui.mock.HistoricalLocation
import moe.fuqiuluo.portal.ui.mock.HistoricalRoute
import moe.fuqiuluo.portal.ui.mock.Rocker
import moe.fuqiuluo.xposed.utils.FakeLoc
import net.sf.geographiclib.Geodesic

class MockServiceViewModel : ViewModel() {
    lateinit var rocker: Rocker
    private lateinit var rockerJob: Job
    private lateinit var routeMockJob: Job
    var isRockerLocked = false
    var routeStage = 0
    val rockerCoroutineController = CoroutineController()
    val routeMockCoroutine = CoroutineRouteMock()

    var isRouteStart = false

    var locationManager: LocationManager? = null
        set(value) {
            field = value
            if (value != null)
                MockServiceHelper.tryInitService(value)
        }

    var selectedLocation: HistoricalLocation? = null
    var selectedRoute: HistoricalRoute? = null


    fun initRocker(activity: Activity): Rocker {
        if (!::rocker.isInitialized) {
            rocker = Rocker(activity)
        }

        if (!::rockerJob.isInitialized || rockerJob.isCancelled) {
            rockerCoroutineController.pause()
            val delayTime = activity.reportDuration.toLong()
            val applicationContext = activity.applicationContext
            rockerJob = GlobalScope.launch {
                do {
                    rockerCoroutineController.controlledCoroutine()
                    delay(delayTime)

                    CrashReport.setUserSceneTag(applicationContext, 261773)
                    if(!MockServiceHelper.move(locationManager!!, FakeLoc.speed / (1000 / delayTime) / 0.85, FakeLoc.bearing)) {
                        Log.e("MockServiceViewModel", "Failed to move")
                    }

//                    if (MockServiceHelper.broadcastLocation(locationManager!!)) {
//                        Log.d("MockServiceViewModel", "Broadcast location")
//                    } else {
//                        Log.e("MockServiceViewModel", "Failed to broadcast location")
//                    }
                } while (isActive)
            }
        }

        FakeLoc.speed = activity.speed
        FakeLoc.altitude = activity.altitude
        FakeLoc.accuracy = activity.accuracy

        if (!::routeMockJob.isInitialized || routeMockJob.isCancelled) {
            routeMockCoroutine.pause()
            val delayTime = activity.reportDuration.toLong()
            routeMockJob = GlobalScope.launch {
                do {
                    routeMockCoroutine.routeMockCoroutine()
                    delay(delayTime)
                    // 如果是第0阶段，定位到第一个点
                    if (routeStage == 0) {
                        MockServiceHelper.setLocation(
                            locationManager!!,
                            selectedRoute!!.route[0].first,
                            selectedRoute!!.route[0].second
                        )
                        routeStage++
                    }
                    val route = selectedRoute!!.route

                    // 处理所有已到达的阶段
                    while (routeStage < route.size) {
                        val target = route[routeStage]
                        val location = MockServiceHelper.getLocation(locationManager!!)
                        val currentLat = location!!.first
                        val currentLon = location.second

                        val inverse = Geodesic.WGS84.Inverse(
                            currentLat,
                            currentLon,
                            target.first,
                            target.second
                        )
                        // 判断距离是否小于1米（可根据需要调整阈值）
                        if (inverse.s12 < 1.0) {
                            // 精确设置位置到目标点并进入下一阶段
                            MockServiceHelper.setLocation(
                                locationManager!!,
                                target.first,
                                target.second
                            )
                            routeStage++
                        } else if (inverse.s12 < FakeLoc.speed / (1000 / delayTime) / 0.85) {
                            // 如果距离小于速度，直接移动到目标点
                            MockServiceHelper.setLocation(
                                locationManager!!,
                                target.first,
                                target.second
                            )
                            routeStage++

                        } else {
                            break
                        }
                    }

                    // 检查是否已完成所有阶段
                    if (routeStage >= route.size) {
                        routeMockCoroutine.pause()
                        rocker.autoStatus = false
                        // 重设阶段
                        routeStage = 0
                        break // 退出循环
                    }

                    // 处理当前目标点的移动
                    val target = route[routeStage]
                    val location = MockServiceHelper.getLocation(locationManager!!)
                    val currentLat = location!!.first
                    val currentLon = location.second

                    val inverse = Geodesic.WGS84.Inverse(
                        currentLat,
                        currentLon,
                        target.first,
                        target.second
                    )
                    var azimuth = inverse.azi1
                    if (azimuth < 0) {
                        azimuth += 360
                    }

                    Log.d("MockServiceViewModel", "从 $currentLat, $currentLon 移动到 ${target.first}, ${target.second}, 方位角: $azimuth")
                    if (!MockServiceHelper.move(
                            locationManager!!,
                            FakeLoc.speed / (1000 / delayTime) / 0.85,
                            azimuth
                        )
                    ) {
                        Log.e("MockServiceViewModel", "移动失败")
                    }
                } while (isActive)
            }
        }

        return rocker
    }

    fun isServiceStart(): Boolean {
        return locationManager != null && MockServiceHelper.isServiceInit() && MockServiceHelper.isMockStart(
            locationManager!!
        )
    }

    // WiFi Hook相关属性
    var isWifiHookEnabled: Boolean = true
        set(value) {
            field = value
            // 这里可以通过某种方式通知Xposed模块更新配置
            Log.i("MockServiceViewModel", "WiFi Hook已${if (value) "启用" else "禁用"}")
        }

    var isWifiDetailLogEnabled: Boolean = false
        set(value) {
            field = value
            Log.i("MockServiceViewModel", "WiFi详细日志已${if (value) "启用" else "禁用"}")
        }

    // WiFi Hook白名单应用
    private val wifiHookWhitelist = mutableSetOf<String>()

    // WiFi Hook黑名单应用（强制拦截）
    private val wifiHookBlacklist = mutableSetOf(
        "com.baidu.BaiduMap",
        "com.autonavi.minimap",
        "com.tencent.map",
        "com.google.android.apps.maps"
    )

    /**
     * 添加应用到WiFi Hook白名单
     */
    fun addToWifiWhitelist(packageName: String) {
        wifiHookWhitelist.add(packageName)
        Log.i("MockServiceViewModel", "已将 $packageName 添加到WiFi Hook白名单")
    }

    /**
     * 从WiFi Hook白名单移除应用
     */
    fun removeFromWifiWhitelist(packageName: String) {
        wifiHookWhitelist.remove(packageName)
        Log.i("MockServiceViewModel", "已将 $packageName 从WiFi Hook白名单移除")
    }

    /**
     * 添加应用到WiFi Hook黑名单
     */
    fun addToWifiBlacklist(packageName: String) {
        wifiHookBlacklist.add(packageName)
        Log.i("MockServiceViewModel", "已将 $packageName 添加到WiFi Hook黑名单")
    }

    /**
     * 从WiFi Hook黑名单移除应用
     */
    fun removeFromWifiBlacklist(packageName: String) {
        wifiHookBlacklist.remove(packageName)
        Log.i("MockServiceViewModel", "已将 $packageName 从WiFi Hook黑名单移除")
    }

    /**
     * 获取WiFi Hook白名单
     */
    fun getWifiWhitelist(): Set<String> = wifiHookWhitelist.toSet()

    /**
     * 获取WiFi Hook黑名单
     */
    fun getWifiBlacklist(): Set<String> = wifiHookBlacklist.toSet()

    /**
     * 重置WiFi Hook配置
     */
    fun resetWifiHookConfig() {
        wifiHookWhitelist.clear()
        wifiHookBlacklist.clear()
        wifiHookBlacklist.addAll(setOf(
            "com.baidu.BaiduMap",
            "com.autonavi.minimap",
            "com.tencent.map",
            "com.google.android.apps.maps"
        ))
        isWifiHookEnabled = true
        isWifiDetailLogEnabled = false
        Log.i("MockServiceViewModel", "WiFi Hook配置已重置")
    }
}