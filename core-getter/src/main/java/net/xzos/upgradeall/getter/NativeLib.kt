package net.xzos.upgradeall.getter

class NativeLib {

    /**
     * A native method that is implemented by the 'getter' native library,
     * which is packaged with this application.
     */
    external fun checkAppAvailable(hub_uuid: String, id_map: Map<String, String>): Boolean
    external fun getAppLatestRelease(hub_uuid: String, id_map: Map<String, String>): String
    external fun getAppReleases(hub_uuid: String, id_map: Map<String, String>): String

    companion object {
        // Used to load the 'getter' library on application startup.
        init {
            System.loadLibrary("api_proxy")
        }
    }
}