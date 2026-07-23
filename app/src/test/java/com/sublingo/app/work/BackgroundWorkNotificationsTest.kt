package com.sublingo.app.work

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class BackgroundWorkNotificationsTest {
    @Test
    fun completionTextIncludesTrimmedVideoTitle() {
        assertEquals(
            "《A Useful Video》的字幕、翻译和生词已生成",
            BackgroundWorkNotifications.completionNotificationText("  A Useful Video  "),
        )
    }

    @Test
    fun completionTextFallsBackWhenTitleIsMissing() {
        assertEquals(
            "字幕、翻译和生词已生成",
            BackgroundWorkNotifications.completionNotificationText("  "),
        )
    }

    @Test
    fun notificationIdIsStablePerVideo() {
        assertEquals(
            BackgroundWorkNotifications.completionNotificationId("video-1"),
            BackgroundWorkNotifications.completionNotificationId("video-1"),
        )
        assertNotEquals(
            BackgroundWorkNotifications.completionNotificationId("video-1"),
            BackgroundWorkNotifications.completionNotificationId("video-2"),
        )
    }
}
