package com.sublingo.app.data.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadRequestPolicyTest {
    @Test fun sixteenKilobyteModeNeverRequestsSeparatedStreamsOrMerge() {
        val attempts = DownloadRequestPolicy.attempts(
            "https://www.youtube.com/watch?v=example",
            ffmpegAvailable = false,
        )

        assertTrue(attempts.isNotEmpty())
        assertTrue(attempts.all { !it.mergeToMp4 })
        assertTrue(attempts.all { '+' !in it.formatSelector })
        assertTrue(attempts.first().label.contains("1080p"))
        assertTrue(attempts.first().nativeMuxAudioSelector != null)
        assertTrue(attempts.first().formatSelector.contains("height<=1080"))
        assertTrue(attempts.any { it.extractorArgs == "youtube:player_client=android_vr" })
        assertEquals(4, attempts.last().hlsConcurrentFragments)
    }

    @Test fun fourKilobyteModeCanPreferHighQualityMergedStreams() {
        val attempts = DownloadRequestPolicy.attempts(
            "https://www.youtube.com/watch?v=example",
            ffmpegAvailable = true,
        )

        assertTrue(attempts.first().mergeToMp4)
        assertTrue(attempts.first().formatSelector.contains("height<=1080"))
        assertTrue(attempts.first().formatSelector.contains('+'))
    }

    @Test fun authenticatedYoutubeSkipsAndroidVrBecauseItRejectsCookies() {
        val attempts = DownloadRequestPolicy.attempts(
            "https://www.youtube.com/watch?v=example",
            ffmpegAvailable = false,
            cookieConfigured = true,
        )

        assertTrue(attempts.none { it.extractorArgs?.contains("android_vr") == true })
        assertTrue(attempts.any { it.extractorArgs?.contains("web_safari") == true })
    }

    @Test fun bilibiliSixteenKilobyteModeDownloadsDashTracksForAndroidMuxing() {
        val first = DownloadRequestPolicy.attempts(
            "https://www.bilibili.com/video/BV1Tf3JesEtJ",
            ffmpegAvailable = false,
        ).first()

        assertTrue(first.label.contains("B站"))
        assertTrue(first.nativeMuxAudioSelector != null)
        assertTrue(first.formatSelector.contains("bestvideo"))
        assertFalse(first.mergeToMp4)
    }

    @Test fun headersAreScopedToTheirOwnSite() {
        val youtube = DownloadRequestPolicy.headers("https://youtu.be/example")
        val bilibili = DownloadRequestPolicy.headers("https://www.bilibili.com/video/BV1xx")
        val xiaohongshu = DownloadRequestPolicy.headers("https://www.xiaohongshu.com/explore/example")

        assertTrue(youtube.isEmpty())
        assertEquals("https://www.bilibili.com/", bilibili.first { it.first == "Referer" }.second)
        assertEquals("https://www.xiaohongshu.com/", xiaohongshu.first { it.first == "Referer" }.second)
        assertFalse(bilibili.any { it.second.contains("xiaohongshu") })
    }

    @Test fun shortDomainsAreRecognized() {
        assertEquals(DownloadSite.YOUTUBE, DownloadRequestPolicy.site("https://youtu.be/abc"))
        assertEquals(DownloadSite.BILIBILI, DownloadRequestPolicy.site("https://b23.tv/abc"))
        assertEquals(DownloadSite.XIAOHONGSHU, DownloadRequestPolicy.site("https://xhslink.com/a/abc"))
    }

    @Test fun rawBrowserCookieBecomesPrivateYtDlpCookieJarContent() {
        val content = DownloadRequestPolicy.netscapeCookieFile(
            "SID=one; HSID=value=with=equals; PREF=zh-CN",
            DownloadRequestPolicy.cookieDomain("https://www.youtube.com/watch?v=abc"),
        )

        assertTrue(content.startsWith("# Netscape HTTP Cookie File"))
        assertTrue(content.contains(".youtube.com\tTRUE\t/\tTRUE\t0\tSID\tone"))
        assertTrue(content.contains("HSID\tvalue=with=equals"))
        assertFalse(content.contains("Cookie:"))
    }

    @Test fun youtubeBotChallengeBecomesActionableCookieMessage() {
        val message = DownloadRequestPolicy.failureMessage(
            "https://www.youtube.com/watch?v=abc",
            listOf(IllegalStateException("Sign in to confirm you’re not a bot. Use --cookies-from-browser")),
            cookieConfigured = false,
        )

        assertTrue(message.contains("App 内登录"))
        assertTrue(message.contains("登录验证"))
    }

    @Test fun netscapeCookieInputIsAcceptedWithoutTreatingCommentsAsCookies() {
        val content = DownloadRequestPolicy.netscapeCookieFile(
            """
            # Netscape HTTP Cookie File
            .youtube.com	TRUE	/	TRUE	0	SID	one
            #HttpOnly_.youtube.com	TRUE	/	TRUE	0	HSID	two
            """.trimIndent(),
            ".youtube.com",
        )

        assertTrue(content.contains("\tSID\tone"))
        assertTrue(content.contains("#HttpOnly_.youtube.com\tTRUE"))
        assertFalse(content.contains("Netscape HTTP Cookie File\t"))
    }

    @Test fun youtubeAnonymous403PointsToCookieSettings() {
        val message = DownloadRequestPolicy.failureMessage(
            "https://youtu.be/abc",
            listOf(IllegalStateException("HTTP Error 403: Forbidden")),
            cookieConfigured = false,
        )

        assertTrue(message.contains("App 内登录"))
        assertTrue(message.contains("403"))
    }

    @Test fun youtube403RequiresInteractiveLoginButOtherSitesDoNot() {
        assertTrue(
            DownloadRequestPolicy.requiresYoutubeLogin(
                "https://www.youtube.com/watch?v=abc",
                listOf(IllegalStateException("HTTP Error 403: Forbidden")),
            ),
        )
        assertFalse(
            DownloadRequestPolicy.requiresYoutubeLogin(
                "https://www.bilibili.com/video/BV1xx",
                listOf(IllegalStateException("HTTP Error 403: Forbidden")),
            ),
        )
    }

    @Test fun visitorCookiesCannotBeMistakenForYoutubeLogin() {
        val cookie = YoutubeLoginCookiePolicy.normalize(
            listOf("YSC=abc; VISITOR_INFO1_LIVE=visitor", "PREF=hl=zh-CN; YSC=duplicate"),
        )
        assertFalse(YoutubeLoginCookiePolicy.hasAuthenticatedSession(cookie))
    }

    @Test fun authenticatedYoutubeCookieCompletesLogin() {
        val cookie = YoutubeLoginCookiePolicy.normalize(
            listOf("YSC=abc; __Secure-3PAPISID=secret", "PREF=hl=zh-CN"),
        )
        assertTrue(YoutubeLoginCookiePolicy.hasAuthenticatedSession(cookie))
        assertEquals(1, cookie.split("YSC=").size - 1)
    }

    @Test fun authenticatedNetscapeCookieAlsoCompletesLoginRefresh() {
        val cookie = ".youtube.com\tTRUE\t/\tTRUE\t0\tSAPISID\tsecret"
        assertTrue(YoutubeLoginCookiePolicy.hasAuthenticatedSession(cookie))
    }
}
