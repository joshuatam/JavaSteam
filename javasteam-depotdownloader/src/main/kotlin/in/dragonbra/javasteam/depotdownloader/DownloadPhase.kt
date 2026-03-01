package `in`.dragonbra.javasteam.depotdownloader

// High level download phases reported by DepotDownloader
enum class DownloadPhase {
    UNKNOWN,
    PREPARING,
    DOWNLOADING,
    VERIFYING,
    COMPLETE,
}
