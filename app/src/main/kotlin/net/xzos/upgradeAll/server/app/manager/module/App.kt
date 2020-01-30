package net.xzos.upgradeAll.server.app.manager.module

import net.xzos.upgradeAll.data.database.litepal.RepoDatabase
import net.xzos.upgradeAll.data.database.manager.AppDatabaseManager
import net.xzos.upgradeAll.data.database.manager.HubDatabaseManager
import net.xzos.upgradeAll.data.json.nongson.ObjectTag
import net.xzos.upgradeAll.server.app.engine.js.JavaScriptEngine
import net.xzos.upgradeAll.server.log.LogUtil
import net.xzos.upgradeAll.utils.VersioningUtils
import org.litepal.LitePal
import org.litepal.extension.find

data class App(private val appDatabaseId: Long) {
    private lateinit var objectTag: ObjectTag
    private val appDatabase
        get() = AppDatabaseManager.getDatabase(appDatabaseId)
    internal val engine = newEngine(appDatabaseId)

    suspend fun isLatest(): Boolean {
        val latestVersion = Updater(appDatabaseId).getLatestVersioning()
        return VersioningUtils.compareVersionNumber(
                markProcessedVersionNumber ?: installedVersioning, latestVersion)
    }

    // 获取已安装版本号
    val installedVersioning: String?
        get() = versioningUtils?.version

    val markProcessedVersionNumber: String?
        get() = appDatabase?.extraData?.markProcessedVersionNumber

    // 获取数据库 VersionCheckerGson 数据
    private val versioningUtils: VersioningUtils.VersionChecker?
        get() {
            val versionChecker = appDatabase?.targetChecker
            return VersioningUtils.VersionChecker(versionChecker)
        }

    // 添加一个 更新检查器追踪子项
    private fun newEngine(databaseId: Long): JavaScriptEngine {
        val repoDatabase: RepoDatabase? = LitePal.find(databaseId)
        val apiUuid = repoDatabase?.api_uuid
        val url = repoDatabase?.url
        val apiName = HubDatabaseManager.getDatabase(apiUuid)?.name
        objectTag = ObjectTag(apiName.toString(), databaseId.toString())
        // 查找软件源数据库
        val jsCode = HubDatabaseManager.getJsCode(apiUuid)
        Log.d(objectTag, TAG, "renewUpdateItem: uuid: $apiUuid")
        return JavaScriptEngine(objectTag, url, jsCode)
    }

    companion object {
        private val Log = LogUtil
        private const val TAG = "App"
    }
}