package com.showroom.kiosk

object Limits {
    const val MAINTENANCE_MS = 10 * 60 * 1000L
    const val MAINTENANCE_EXTEND_MS = 30 * 60 * 1000L
    const val TAP_COUNT = 7
    const val TAP_WINDOW_MS = 4_000L
    const val SYNC_INTERVAL_MS = 5 * 60 * 1000L
    const val MAX_DOWNLOAD_BYTES = 80L * 1024L * 1024L
}
