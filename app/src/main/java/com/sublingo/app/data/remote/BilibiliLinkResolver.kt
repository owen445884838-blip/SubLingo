package com.sublingo.app.data.remote

import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.regex.Pattern
import javax.inject.Inject
import javax.inject.Singleton

data class BilibiliVideoMeta(
    val title: String,
    val author: String,
    val coverUrl: String,
    val canonicalUrl: String,
    val bvid: String,
)

@Singleton
class BilibiliLinkResolver @Inject constructor(
    private val client: OkHttpClient,
) {
    fun resolve(url: String): BilibiliVideoMeta {
        val normalized = url.trim()
        val html = fetchHtml(normalized)
        val title = extractMeta(html, "og:title") ?: extractTitle(html) ?: "Bilibili 视频"
        val cover = extractMeta(html, "og:image") ?: ""
        val author = extractMeta(html, "author") ?: "Bilibili"
        val bvid = extractBvid(normalized, html)
        return BilibiliVideoMeta(
            title = title,
            author = author,
            coverUrl = cover,
            canonicalUrl = "https://www.bilibili.com/video/$bvid",
            bvid = bvid,
        )
    }

    private fun fetchHtml(url: String): String {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 SubLingo/1.0")
            .header("Referer", "https://www.bilibili.com")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("Bilibili 解析失败：${response.code}")
            return response.body?.string().orEmpty()
        }
    }

    private fun extractMeta(html: String, property: String): String? {
        val pattern = Pattern.compile("<meta[^>]+(?:property|name)=['\"]${Regex.escape(property)}['\"][^>]+content=['\"]([^'\"]+)['\"][^>]*>", Pattern.CASE_INSENSITIVE)
        val matcher = pattern.matcher(html)
        return if (matcher.find()) matcher.group(1) else null
    }

    private fun extractTitle(html: String): String? {
        val pattern = Pattern.compile("<title>(.*?)</title>", Pattern.CASE_INSENSITIVE or Pattern.DOTALL)
        val matcher = pattern.matcher(html)
        return if (matcher.find()) matcher.group(1)?.replace(" - 哔哩哔哩", "")?.trim() else null
    }

    private fun extractBvid(url: String, html: String): String {
        Regex("BV[0-9A-Za-z]{10}").find(url)?.value?.let { return it }
        Regex("BV[0-9A-Za-z]{10}").find(html)?.value?.let { return it }
        return "BV-UNKNOWN"
    }
}
