package net.xzos.upgradeall.core.network_api

import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import io.grpc.StatusRuntimeException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import net.xzos.upgradeall.core.data.config.AppConfig
import net.xzos.upgradeall.core.data.json.nongson.ObjectTag
import net.xzos.upgradeall.core.data_manager.utils.DataCache
import net.xzos.upgradeall.core.log.Log
import net.xzos.upgradeall.core.route.*


object GrpcApi {

    private const val TAG = "GrpcApi"
    private val logObjectTag = ObjectTag(ObjectTag.core, TAG)
    private val invalidHubUuidList: MutableList<String> = mutableListOf()
    private var mChannel: ManagedChannel = ManagedChannelBuilder.forTarget(AppConfig.update_server_url).usePlaintext().build()

    init {
        renew()
    }

    fun renew() {
        mChannel = ManagedChannelBuilder.forTarget(AppConfig.update_server_url).usePlaintext().build()
    }

    suspend fun getAppStatusList(hubUuid: String, appIdList: MutableList<List<AppIdItem>>): List<ResponsePackage>? {
        if (hubUuid in invalidHubUuidList) return null
        val responseList: MutableList<ResponsePackage> = mutableListOf()
        for (appId in appIdList.toList()) {
            if (DataCache.existsAppStatus(hubUuid, appId)) {
                responseList.add(ResponsePackage.newBuilder()
                        .addAllAppId(appId).setAppStatus(
                                DataCache.getAppStatus(hubUuid, appId)
                        ).build()
                )
                appIdList.remove(appId)
            }
        }
        val blockingStub = UpdateServerRouteGrpc.newBlockingStub(mChannel)
        val request = RequestList.newBuilder()
                .setHubUuid(hubUuid)
                .addAllAppIdList(appIdList.map {
                    AppId.newBuilder().addAllAppId(it).build()
                }).build()
        try {
            val responseList1 = withTimeout(15000L) {
                blockingStub.getAppStatusList(request)
            }.responseList
            for (responsePackage in responseList1) {
                DataCache.cacheReleaseInfo(hubUuid, responsePackage.appIdList, responsePackage.appStatus)
            }
            if (responseList.size == 1 && responseList[0].appStatus.validHubUuid) {
                invalidHubUuidList.add(hubUuid)
                return null
            }
        } catch (e: TimeoutCancellationException) {
            Log.w(logObjectTag, TAG, """请求超时，取消
                hub_uuid: $hubUuid
                app_info: $appIdList
            """.trimIndent())
        } catch (ignore: StatusRuntimeException) {
        }
        return responseList
    }

    suspend fun getAppStatus(hubUuid: String, appId: List<AppIdItem>): AppStatus? {
        if (hubUuid in invalidHubUuidList) return null
        if (DataCache.existsAppStatus(hubUuid, appId))
            return DataCache.getAppStatus(hubUuid, appId)
        val blockingStub = UpdateServerRouteGrpc.newBlockingStub(mChannel)
        val request = buildRequest(hubUuid, appId)
        val returnValue = try {
            withTimeout(15000L) {
                blockingStub.getAppStatus(request)
            }
        } catch (e: TimeoutCancellationException) {
            Log.w(logObjectTag, TAG, """请求超时，取消
                hub_uuid: $hubUuid
                app_info: $appId
            """.trimIndent())
            return null
        } catch (ignore: StatusRuntimeException) {
            return null
        }
        return if (!returnValue.validHubUuid) {
            invalidHubUuidList.add(hubUuid)
            null
        } else {
            DataCache.cacheReleaseInfo(hubUuid, appId, returnValue)
            returnValue
        }
    }

    suspend fun getDownloadInfo(hubUuid: String, appId: List<AppIdItem>, assetIndex: List<Int>): DownloadInfo? {
        if (hubUuid in invalidHubUuidList) return null
        val blockingStub = UpdateServerRouteGrpc.newBlockingStub(mChannel)
        val request = DownloadAssetIndex.newBuilder().setAppIdInfo(
                buildRequest(hubUuid, appId)
        ).apply {
            for (i in assetIndex)
                addAssetIndex(i)
        }.build()
        return try {
            blockingStub.getDownloadInfo(request)
        } catch (ignore: StatusRuntimeException) {
            null
        }
    }

    private fun buildRequest(hubUuid: String, appId: List<AppIdItem>) =
            Request.newBuilder().setHubUuid(hubUuid).apply {
                for (infoItem in appId) {
                    addAppId(AppIdItem.newBuilder().setKey(infoItem.key).setValue(infoItem.value).build())
                }
            }.build()
}