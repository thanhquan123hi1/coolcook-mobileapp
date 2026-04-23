package com.coolcook.app.core.util

object AvatarImageUtils {

    @JvmStatic
    fun buildOptimizedAvatarUrl(rawUrl: String?, sizePx: Int): String {
        return buildOptimizedAvatarUrl(rawUrl, sizePx, sizePx)
    }

    @JvmStatic
    fun buildOptimizedAvatarUrl(rawUrl: String?, widthPx: Int, heightPx: Int): String {
        val safeUrl = rawUrl?.trim().orEmpty()
        if (safeUrl.isEmpty()) {
            return ""
        }

        val marker = "/image/upload/"
        val markerIndex = safeUrl.indexOf(marker)
        if (markerIndex == -1) {
            return safeUrl
        }

        val pathAfterUpload = safeUrl.substring(markerIndex + marker.length)
        val firstSegment = pathAfterUpload.substringBefore('/')
        val alreadyTransformed = firstSegment.contains(",") &&
            (firstSegment.contains("c_")
                || firstSegment.contains("f_")
                || firstSegment.contains("g_")
                || firstSegment.contains("h_")
                || firstSegment.contains("q_")
                || firstSegment.contains("w_")
                || firstSegment.contains("dpr_"))

        if (alreadyTransformed) {
            return safeUrl
        }

        val transformation = "c_fill,g_auto,h_${heightPx.coerceAtLeast(1)},w_${widthPx.coerceAtLeast(1)},f_auto,q_auto,dpr_auto/"
        return safeUrl.replaceFirst(marker, marker + transformation)
    }
}
