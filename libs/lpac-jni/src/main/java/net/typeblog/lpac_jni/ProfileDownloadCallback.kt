package net.typeblog.lpac_jni

fun interface ProfileDownloadCallback {
    fun onStateUpdate(state: ProfileDownloadState)
}
