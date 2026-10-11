package dev.offlinettspersian.tts

sealed class ModelStatus {
    object NotDownloaded : ModelStatus()
    object Downloaded : ModelStatus()
    object Installed : ModelStatus()
    data class Incomplete(val missing: List<String>) : ModelStatus()

    fun displayText(): String = when (this) {
        is NotDownloaded -> "Not downloaded"
        is Downloaded -> "Downloaded — installation needed"
        is Installed -> "Installed — ready"
        is Incomplete -> "Incomplete — needs repair"
    }
}
