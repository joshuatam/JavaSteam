package `in`.dragonbra.javasteam.steam.handlers.steamcloud

import `in`.dragonbra.javasteam.protobufs.steamclient.SteammessagesCloudSteamclient.CCloud_GetAppFileChangelist_Response

@Suppress("unused")
class AppFileChangeList {
    val currentChangeNumber: Long
    val files: List<AppFileInfo>
    val isOnlyDelta: Boolean
    val pathPrefixes: List<String>
    val machineNames: List<String>
    val appBuildIDHwm: Long

    constructor (response: CCloud_GetAppFileChangelist_Response.Builder) {
        this.currentChangeNumber = response.currentChangeNumber
        this.isOnlyDelta = response.isOnlyDelta
        this.machineNames = response.machineNamesList
        this.appBuildIDHwm = response.appBuildidHwm

        /**
         * NOTE: Some files can still have prefixes from the response,
         *   might be an issue with the SteamKit.
         *   Added a workaround to handle the issue.
         * ie. in Bug Fables
         * {
         *   files [
         *     { fileName: "%GameInstall%save0.dat", ... },
         *     { fileName: "%GameInstall%save1.dat", ... },
         *     { fileName: "%GameInstall%save2.dat", ... }
         *   ]
         *   pathPrefixes: []
         * }
         */
        if (response.filesList.isEmpty() || !response.filesList[0].fileName.startsWith("%")) {
            this.files = response.filesList.map { AppFileInfo(it.fileName, it) }
            this.pathPrefixes = response.pathPrefixesList
            return
        }

        // Remove the prefix from the filenames
        val filename = response.filesList[0].fileName
        val index = filename.indexOf('%', 1) + 1
        this.files = response.filesList.map {
            AppFileInfo(it.fileName.substring(index), it)
        }
        this.pathPrefixes = listOf(filename.substring(0, index))
    }
}
