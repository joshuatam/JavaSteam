package `in`.dragonbra.javasteam.steam.handlers.steamcloud

import `in`.dragonbra.javasteam.protobufs.steamclient.Enums.ECloudStoragePersistState
import `in`.dragonbra.javasteam.protobufs.steamclient.SteammessagesCloudSteamclient.CCloud_AppFileInfo
import java.util.Date

class AppFileInfo(filename: String, response: CCloud_AppFileInfo) {
    val filename: String = filename
    val shaFile: ByteArray = response.shaFile.toByteArray()
    val timestamp: Date = Date(response.timeStamp * 1000L)
    val rawFileSize: Int = response.rawFileSize
    val persistState: ECloudStoragePersistState = response.persistState
    val platformsToSync: Int = response.platformsToSync
    val pathPrefixIndex: Int = response.pathPrefixIndex
    val machineNameIndex: Int = response.machineNameIndex
}
