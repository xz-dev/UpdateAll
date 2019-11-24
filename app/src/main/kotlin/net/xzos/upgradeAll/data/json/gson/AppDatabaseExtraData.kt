package net.xzos.upgradeAll.data.json.gson

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName

data class AppDatabaseExtraData(
        @SerializedName("cloud_app_config") private var cloudAppConfig: String? = null  // TODO: 尝试类型优化
) {
    fun getCloudAppConfig(): AppConfig? {
        return if (cloudAppConfig != null)
            Gson().fromJson(cloudAppConfig, AppConfig::class.java)
        else null
    }
}
