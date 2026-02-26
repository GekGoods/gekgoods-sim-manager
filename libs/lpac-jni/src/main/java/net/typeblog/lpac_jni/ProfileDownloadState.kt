package net.typeblog.lpac_jni

enum class ProfileDownloadState(val progress: Int) {
    Preparing(0),
    Connecting(20),  // Before {server,client} authentication
    Authenticating(40),  // {server,client} authentication
    Downloading(60),  // prepare download, get bpp from es9p
    Finalizing(80); // load bpp

    companion object {
        fun lookupStateFromProgress(progress: Int) =
            entries.first { it.progress == progress }
    }
}
