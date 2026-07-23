package com.sublingo.app.data.media

enum class DownloadSite { YOUTUBE, BILIBILI, XIAOHONGSHU, OTHER }

data class DownloadAttempt(
    val label: String,
    val formatSelector: String,
    val mergeToMp4: Boolean = false,
    val nativeMuxAudioSelector: String? = null,
    val extractorArgs: String? = null,
    val forceIpv4: Boolean = false,
    val hlsConcurrentFragments: Int = 1,
)

/** Site- and runtime-aware yt-dlp policy. Keeping this pure makes it possible to verify that a
 * 16 KB device can never accidentally select a format which requires the incompatible FFmpeg
 * package, and that site-specific anti-hotlink headers never leak to another provider. */
object DownloadRequestPolicy {
    private const val YOUTUBE_PROGRESSIVE_MP4 =
        "best[protocol^=http][ext=mp4][height<=1080][vcodec!=none][acodec!=none]/22/18"

    private const val SINGLE_FILE =
        "best[ext=mp4][height<=1080][vcodec!=none][acodec!=none]/" +
            "best[ext=mp4][vcodec!=none][acodec!=none]/" +
            "best[vcodec!=none][acodec!=none]"

    private const val MERGED_HIGH_QUALITY =
        "bestvideo[height<=1080][ext=mp4][vcodec^=avc1]+bestaudio[ext=m4a]/" +
            "bestvideo[height<=1080][vcodec!=none]+bestaudio[acodec!=none]/$SINGLE_FILE"

    private const val YOUTUBE_NATIVE_1080_VIDEO =
        "bestvideo[height<=1080][ext=mp4][vcodec^=avc1]/bestvideo[height<=1080][ext=mp4]"
    private const val YOUTUBE_NATIVE_AUDIO =
        "bestaudio[ext=m4a][acodec^=mp4a]/bestaudio[ext=m4a]"

    private const val BILIBILI_NATIVE_VIDEO =
        "bestvideo[ext=mp4][vcodec^=avc1]/bestvideo[ext=mp4]"
    private const val BILIBILI_NATIVE_AUDIO =
        "bestaudio[ext=m4a][acodec^=mp4a]/bestaudio[ext=m4a]/bestaudio[acodec^=mp4a]"

    fun site(url: String): DownloadSite {
        val host = runCatching { java.net.URI(url).host.orEmpty().lowercase() }.getOrDefault("")
        return when {
            host == "youtu.be" || host.endsWith(".youtube.com") || host == "youtube.com" -> DownloadSite.YOUTUBE
            host.endsWith(".bilibili.com") || host == "bilibili.com" || host == "b23.tv" -> DownloadSite.BILIBILI
            host.endsWith(".xiaohongshu.com") || host == "xiaohongshu.com" || host == "xhslink.com" -> DownloadSite.XIAOHONGSHU
            else -> DownloadSite.OTHER
        }
    }

    fun attempts(url: String, ffmpegAvailable: Boolean, cookieConfigured: Boolean = false): List<DownloadAttempt> {
        val first = if (ffmpegAvailable) {
            DownloadAttempt("高画质音视频", MERGED_HIGH_QUALITY, mergeToMp4 = true)
        } else {
            DownloadAttempt("16KB 兼容单文件", SINGLE_FILE)
        }
        return when (site(url)) {
            DownloadSite.YOUTUBE -> buildList {
                if (ffmpegAvailable) {
                    add(first)
                } else {
                    add(
                        DownloadAttempt(
                            label = "YouTube 1080p 原生音视频合并",
                            formatSelector = YOUTUBE_NATIVE_1080_VIDEO,
                            nativeMuxAudioSelector = YOUTUBE_NATIVE_AUDIO,
                        ),
                    )
                }
                add(DownloadAttempt("YouTube 直连 MP4", YOUTUBE_PROGRESSIVE_MP4))
                add(DownloadAttempt("YouTube Safari 直连 MP4", YOUTUBE_PROGRESSIVE_MP4, extractorArgs = "youtube:player_client=web_safari", forceIpv4 = true))
                if (!cookieConfigured) {
                    add(DownloadAttempt("YouTube Android VR 直连 MP4", YOUTUBE_PROGRESSIVE_MP4, extractorArgs = "youtube:player_client=android_vr", forceIpv4 = true))
                }
                add(DownloadAttempt("YouTube HLS 兼容流", SINGLE_FILE, forceIpv4 = true, hlsConcurrentFragments = 4))
            }.distinctBy { listOf(it.formatSelector, it.extractorArgs, it.forceIpv4, it.hlsConcurrentFragments) }
            DownloadSite.BILIBILI -> if (ffmpegAvailable) {
                listOf(first, DownloadAttempt("B站兼容单文件", SINGLE_FILE, forceIpv4 = true))
                    .distinctBy { listOf(it.formatSelector, it.forceIpv4) }
            } else {
                listOf(
                    DownloadAttempt(
                        label = "B站 16KB 原生音视频合并",
                        formatSelector = BILIBILI_NATIVE_VIDEO,
                        nativeMuxAudioSelector = BILIBILI_NATIVE_AUDIO,
                    ),
                    DownloadAttempt("B站兼容单文件", SINGLE_FILE, forceIpv4 = true),
                )
            }
            else -> listOf(first, DownloadAttempt("兼容单文件", SINGLE_FILE, forceIpv4 = true))
                .distinctBy { listOf(it.formatSelector, it.forceIpv4) }
        }
    }

    fun headers(url: String): List<Pair<String, String>> = when (site(url)) {
        DownloadSite.BILIBILI -> listOf(
            "User-Agent" to DESKTOP_USER_AGENT,
            "Referer" to "https://www.bilibili.com/",
            "Origin" to "https://www.bilibili.com",
            "Accept-Language" to "zh-CN,zh;q=0.9,en;q=0.8",
        )
        DownloadSite.XIAOHONGSHU -> listOf(
            "User-Agent" to DESKTOP_USER_AGENT,
            "Referer" to "https://www.xiaohongshu.com/",
            "Accept-Language" to "zh-CN,zh;q=0.9,en;q=0.8",
        )
        // yt-dlp derives the correct media headers for its selected YouTube client. Overriding
        // them with another site's Origin/Referer or a mismatched UA can make CDN URLs return 403.
        DownloadSite.YOUTUBE -> emptyList()
        DownloadSite.OTHER -> listOf("User-Agent" to DESKTOP_USER_AGENT)
    }

    fun cookieDomain(url: String): String = when (site(url)) {
        DownloadSite.YOUTUBE -> ".youtube.com"
        DownloadSite.BILIBILI -> ".bilibili.com"
        DownloadSite.XIAOHONGSHU -> ".xiaohongshu.com"
        DownloadSite.OTHER -> runCatching { java.net.URI(url).host.orEmpty() }
            .getOrDefault("")
            .let { host -> if (host.isBlank()) ".invalid" else ".${host.removePrefix("www.")}" }
    }

    fun failureMessage(url: String, errors: List<Throwable>, cookieConfigured: Boolean): String {
        val combined = errors.joinToString("\n") { it.message.orEmpty() }.lowercase()
        return when {
            site(url) == DownloadSite.YOUTUBE && (
                "sign in to confirm" in combined ||
                    "not a bot" in combined ||
                    "cookies-from-browser" in combined
            ) -> if (cookieConfigured) {
                "YouTube 登录会话已失效。请在 App 内重新登录 YouTube 后自动重试。"
            } else {
                "YouTube 要求登录验证。请在 App 内登录 YouTube，完成后将自动重试。"
            }
            "http error 403" in combined || "forbidden" in combined ->
                if (site(url) == DownloadSite.YOUTUBE) {
                    if (cookieConfigured) {
                        "YouTube 拒绝了当前登录会话（HTTP 403）。请在 App 内重新登录后自动重试。"
                    } else {
                        "YouTube 拒绝匿名媒体请求（HTTP 403）。请在 App 内登录，完成后将自动重试。"
                    }
                } else {
                    "视频站点拒绝了媒体请求（HTTP 403）。请更新该站点 Cookie 后重试。"
                }
            else -> errors.lastOrNull()?.message ?: "所有兼容下载策略均失败"
        }
    }

    fun requiresYoutubeLogin(url: String, errors: List<Throwable>): Boolean {
        if (site(url) != DownloadSite.YOUTUBE) return false
        val combined = errors.joinToString("\n") { it.message.orEmpty() }.lowercase()
        return "sign in to confirm" in combined ||
            "not a bot" in combined ||
            "cookies-from-browser" in combined ||
            "http error 403" in combined ||
            "forbidden" in combined ||
            "youtube 要求登录验证" in combined ||
            "youtube 登录验证仍未通过" in combined ||
            "youtube 登录会话已失效" in combined ||
            "youtube 拒绝了媒体请求" in combined ||
            "youtube 拒绝了当前登录会话" in combined ||
            "youtube 拒绝匿名媒体请求" in combined
    }

    fun netscapeCookieFile(cookieHeader: String, domain: String): String {
        val normalizedLines = cookieHeader.lineSequence().map(String::trim).filter(String::isNotBlank).toList()
        val netscapeRows = normalizedLines.filter { line ->
            val candidate = line.removePrefix("#HttpOnly_")
            candidate.count { it == '\t' } >= 6
        }
        if (netscapeRows.isNotEmpty()) {
            return buildString {
                appendLine("# Netscape HTTP Cookie File")
                netscapeRows.forEach(::appendLine)
            }
        }

        val rows = normalizedLines
            .asSequence()
            .map { line -> line.removePrefix("Cookie:").removePrefix("cookie:").trim() }
            .flatMap { line -> line.split(';').asSequence() }
            .map(String::trim)
            .filter { it.isNotBlank() && '=' in it }
            .mapNotNull { pair ->
                val name = pair.substringBefore('=').trim()
                val value = pair.substringAfter('=', "").trim()
                if (name.isBlank() || name.startsWith('#')) null
                else "$domain\tTRUE\t/\tTRUE\t0\t$name\t$value"
            }
            .distinct()
            .toList()
        require(rows.isNotEmpty()) { "Cookie 格式无效，请粘贴 name=value; name2=value 形式的浏览器 Cookie" }
        return buildString {
            appendLine("# Netscape HTTP Cookie File")
            rows.forEach(::appendLine)
        }
    }

    private const val DESKTOP_USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"
}

/** Keeps WebView-specific login completion checks out of the UI. YouTube always creates visitor
 * cookies, so YSC/PREF/VISITOR_INFO1_LIVE must never be treated as proof that the user signed in. */
object YoutubeLoginCookiePolicy {
    private val authenticatedCookieNames = setOf(
        "SAPISID",
        "__Secure-1PAPISID",
        "__Secure-3PAPISID",
        "LOGIN_INFO",
    )

    fun normalize(cookieHeaders: List<String?>): String = cookieHeaders
        .asSequence()
        .filterNotNull()
        .flatMap { it.split(';').asSequence() }
        .map(String::trim)
        .filter { it.isNotBlank() && '=' in it }
        .distinctBy { it.substringBefore('=').trim() }
        .joinToString("; ")

    fun hasAuthenticatedSession(cookieHeader: String): Boolean {
        val names = cookieHeader.lineSequence()
            .asSequence()
            .flatMap { line -> line.split(';').asSequence() }
            .map { token ->
                val columns = token.trim().split('\t')
                if (columns.size >= 7) columns[5].trim() else token.substringBefore('=').trim()
            }
            .filter(String::isNotBlank)
            .toSet()
        return names.any(authenticatedCookieNames::contains)
    }
}
