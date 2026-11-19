package com.chaptervoice.app.core

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HlsTest {
    @Test fun masterPlaylistBestFirst() {
        val text = """
            #EXTM3U
            #EXT-X-STREAM-INF:BANDWIDTH=800000,RESOLUTION=640x360,CODECS="avc1.4d401e,mp4a.40.2"
            360/index.m3u8
            #EXT-X-STREAM-INF:BANDWIDTH=5000000,RESOLUTION=1920x1080
            https://cdn.x.com/1080/index.m3u8
            #EXT-X-STREAM-INF:BANDWIDTH=2500000,RESOLUTION=1280x720
            720/index.m3u8?token=a
        """.trimIndent()
        assertTrue(Hls.isMaster(text))
        val v = Hls.variants(text, "https://x.com/video/master.m3u8")
        assertEquals(listOf("1080p", "720p", "360p"), v.map { it.label })
        assertEquals("https://x.com/video/720/index.m3u8?token=a", v[1].url)
        assertEquals("https://cdn.x.com/1080/index.m3u8", v[0].url)
    }

    @Test fun mediaPlaylistWithKeys() {
        val text = """
            #EXTM3U
            #EXT-X-TARGETDURATION:10
            #EXT-X-MEDIA-SEQUENCE:7
            #EXT-X-KEY:METHOD=AES-128,URI="key.bin"
            #EXTINF:10.0,
            seg7.ts
            #EXTINF:9.5,
            seg8.ts
            #EXT-X-KEY:METHOD=AES-128,URI="https://k.com/k2",IV=0x000102030405060708090a0b0c0d0e0f
            #EXTINF:4,
            /abs/seg9.ts
            #EXT-X-KEY:METHOD=NONE
            #EXTINF:1,
            seg10.ts
            #EXT-X-ENDLIST
        """.trimIndent()
        val p = Hls.mediaPlaylist(text, "https://x.com/v/720/index.m3u8")
        assertEquals(4, p.segments.size)
        assertEquals(24.5, p.duration, 0.001)
        assertFalse(p.live)
        assertFalse(p.drm)
        assertTrue(p.encrypted)
        assertNull(p.initUrl)
        assertEquals("https://x.com/v/720/seg7.ts", p.segments[0].url)
        assertEquals("https://x.com/v/720/key.bin", p.segments[0].key?.url)
        assertEquals("https://x.com/abs/seg9.ts", p.segments[2].url)
        assertNull(p.segments[3].key)
        // No IV given: the sequence number, big-endian.
        assertArrayEquals(ByteArray(15) + byteArrayOf(8), Hls.ivFor(p.segments[1]))
        assertArrayEquals(ByteArray(16) { it.toByte() }, Hls.ivFor(p.segments[2]))
    }

    @Test fun fmp4LiveAndDrm() {
        val fmp4 = Hls.mediaPlaylist("#EXTM3U\n#EXT-X-MAP:URI=\"init.mp4\"\n#EXTINF:6,\na.m4s\n", "https://x.com/v/i.m3u8")
        assertEquals("https://x.com/v/init.mp4", fmp4.initUrl)
        assertTrue(fmp4.live)
        val drm = Hls.mediaPlaylist(
            "#EXTM3U\n#EXT-X-KEY:METHOD=SAMPLE-AES,URI=\"skd://x\",KEYFORMAT=\"com.apple.streamingkeydelivery\"\n#EXTINF:6,\na.ts\n#EXT-X-ENDLIST",
            "https://x.com/v/i.m3u8",
        )
        assertTrue(drm.drm)
    }
}
