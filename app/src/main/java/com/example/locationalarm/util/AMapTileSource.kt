package com.example.locationalarm.util

import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.util.MapTileIndex
import java.util.Random

/**
 * AMap (Gaode) tile source for OSMDroid.
 *
 * Uses AMap's public tile servers (no API key required for tiles).
 * Displays Chinese road maps with Chinese labels.
 *
 * This is a custom OnlineTileSourceBase implementation that properly handles
 * query-string URLs without appending any file extension suffix.
 */
object AMapTileSource {

    private val random = Random()

    /**
     * Standard AMap road map with Chinese labels.
     * Uses multiple subdomains for load balancing (01-04).
     */
    val ROAD_MAP: OnlineTileSourceBase = object : OnlineTileSourceBase(
        "AMap-Road",
        1, 18,
        256, "",
        arrayOf(
            "https://webrd01.is.autonavi.com/appmaptile?lang=zh_cn&size=1&scale=1&style=8&x=%d&y=%d&z=%d",
            "https://webrd02.is.autonavi.com/appmaptile?lang=zh_cn&size=1&scale=1&style=8&x=%d&y=%d&z=%d",
            "https://webrd03.is.autonavi.com/appmaptile?lang=zh_cn&size=1&scale=1&style=8&x=%d&y=%d&z=%d",
            "https://webrd04.is.autonavi.com/appmaptile?lang=zh_cn&size=1&scale=1&style=8&x=%d&y=%d&z=%d",
        ),
        "\u00a9 AutoNavi"
    ) {
        override fun getTileURLString(pMapTileIndex: Long): String {
            val url = baseUrls[random.nextInt(baseUrls.size)]
            return String.format(
                url,
                MapTileIndex.getX(pMapTileIndex),
                MapTileIndex.getY(pMapTileIndex),
                MapTileIndex.getZoom(pMapTileIndex)
            )
        }
    }

    /**
     * AMap satellite imagery.
     */
    val SATELLITE: OnlineTileSourceBase = object : OnlineTileSourceBase(
        "AMap-Satellite",
        1, 18,
        256, "",
        arrayOf(
            "https://webst01.is.autonavi.com/appmaptile?style=6&x=%d&y=%d&z=%d",
            "https://webst02.is.autonavi.com/appmaptile?style=6&x=%d&y=%d&z=%d",
            "https://webst03.is.autonavi.com/appmaptile?style=6&x=%d&y=%d&z=%d",
            "https://webst04.is.autonavi.com/appmaptile?style=6&x=%d&y=%d&z=%d",
        ),
        "\u00a9 AutoNavi"
    ) {
        override fun getTileURLString(pMapTileIndex: Long): String {
            val url = baseUrls[random.nextInt(baseUrls.size)]
            return String.format(
                url,
                MapTileIndex.getX(pMapTileIndex),
                MapTileIndex.getY(pMapTileIndex),
                MapTileIndex.getZoom(pMapTileIndex)
            )
        }
    }
}
