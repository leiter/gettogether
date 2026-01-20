package com.gettogether.app.coil

import android.util.Base64
import android.util.Log
import coil3.ImageLoader
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import okio.Buffer
import java.io.File

/**
 * Custom Coil Fetcher that loads avatar images from Jami daemon's vCard files.
 *
 * vCard files contain profile data including base64-encoded photos.
 * This fetcher parses the vCard and extracts the photo data directly,
 * eliminating the need for separate avatar file storage.
 *
 * Usage: Pass a vCard file path (ending in .vcf) as the image data.
 */
class VCardFetcher(
    private val data: VCardData,
    private val options: Options
) : Fetcher {

    companion object {
        private const val TAG = "VCardFetcher"
    }

    override suspend fun fetch(): FetchResult? {
        val vcardPath = data.path
        Log.d(TAG, "Fetching avatar from vCard: $vcardPath")

        val vcardFile = File(vcardPath)
        if (!vcardFile.exists()) {
            Log.w(TAG, "vCard file not found: $vcardPath")
            return null
        }

        try {
            val vcardContent = vcardFile.readText()
            val photoBase64 = extractPhoto(vcardContent)

            if (photoBase64 == null) {
                Log.d(TAG, "No photo found in vCard: $vcardPath")
                return null
            }

            // Decode base64 to bytes
            val imageBytes = Base64.decode(photoBase64, Base64.DEFAULT)
            if (imageBytes.isEmpty()) {
                Log.w(TAG, "Decoded photo is empty")
                return null
            }

            Log.d(TAG, "Extracted avatar from vCard (${imageBytes.size} bytes)")

            // Create ImageSource from bytes
            val buffer = Buffer().write(imageBytes)
            val imageSource = ImageSource(
                source = buffer,
                fileSystem = okio.FileSystem.SYSTEM,
            )

            return SourceFetchResult(
                source = imageSource,
                mimeType = "image/jpeg",  // vCard photos are typically JPEG
                dataSource = DataSource.DISK
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract avatar from vCard: ${e.message}", e)
            return null
        }
    }

    /**
     * Extract base64 photo data from vCard content.
     */
    private fun extractPhoto(vcard: String): String? {
        // PHOTO field format: PHOTO;ENCODING=BASE64;TYPE=PNG:base64data...
        // Data may span multiple lines
        val photoPattern = Regex(
            "(?i)PHOTO;[^:]*:([A-Za-z0-9+/=\\s]+?)(?=\r?\n[A-Z]|\r?\nEND:)",
            RegexOption.DOT_MATCHES_ALL
        )

        val match = photoPattern.find(vcard)
        if (match != null) {
            // Remove whitespace from base64 data
            return match.groupValues[1].replace(Regex("\\s"), "")
        }
        return null
    }

    class Factory : Fetcher.Factory<VCardData> {
        override fun create(data: VCardData, options: Options, imageLoader: ImageLoader): Fetcher {
            return VCardFetcher(data, options)
        }
    }
}

/**
 * Wrapper class for vCard file paths.
 * Coil uses this to identify data that should be handled by VCardFetcher.
 */
data class VCardData(val path: String)

/**
 * Mapper that converts String paths ending in .vcf to VCardData.
 * This allows the UI to pass vCard paths as regular strings.
 */
class VCardMapper : coil3.map.Mapper<String, VCardData> {
    override fun map(data: String, options: Options): VCardData? {
        return if (data.endsWith(".vcf", ignoreCase = true)) {
            VCardData(data)
        } else {
            null  // Let default file handler process it
        }
    }
}
