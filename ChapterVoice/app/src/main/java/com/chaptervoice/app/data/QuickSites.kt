package com.chaptervoice.app.data

/** Web novel sites shown on the start page of a new tab, grouped by language. */
data class SiteGroup(val id: String, val label: String, val sites: List<String>)

object QuickSites {
    val groups: List<SiteGroup> = listOf(
        SiteGroup(
            "en", "🇬🇧 English",
            listOf(
                "akknovel.com", "archiveofourown.org", "asianovel.net", "azurechronicles.com", "baka-tsuki.org",
                "beastnovels.com", "chikari.moe", "curspe.com", "dreamy-translations.com", "empirenovel.com",
                "fanfiction.net", "fanmtl.com", "fenrirealm.com", "freewebnovel.com", "fucknovelpia.com",
                "inkitt.com", "mattreadingtranslations.com", "novelarrow.com", "novelbuddy.me", "noveldex.io",
                "novelfire.net", "novelfull.net", "novellunar.com", "novelnext.me", "novelphoenix.com",
                "novelpia.com", "novelping.com", "novgo.net", "re-library.com", "revengernovel.com",
                "roliascan.com", "royalroad.com", "scribblehub.com", "shanghaifantasy.com", "webnovel.com",
                "wetriedtls.com", "wtr-lab.com", "wuxiaworld.com", "wuxiaworld.site",
            ),
        ),
        SiteGroup(
            "zh", "🇨🇳 中文",
            listOf(
                "52shuku.net", "book.qq.com", "ixdzs8.com", "jjwxc.net", "qidian.com",
                "ttkan.co", "xbanxia.cc", "xbiquge.info", "zhulang.com", "zongheng.com",
            ),
        ),
        SiteGroup(
            "ko", "🇰🇷 한국어",
            listOf("joara.com", "munpia.com", "novelpia.com", "series.naver.com"),
        ),
        SiteGroup(
            "pt", "🇧🇷 Português",
            listOf("centralnovel.com", "novelmania.com.br"),
        ),
        SiteGroup(
            "ru", "🇷🇺 Русский",
            listOf("author.today", "ranobelib.me"),
        ),
    )
}
