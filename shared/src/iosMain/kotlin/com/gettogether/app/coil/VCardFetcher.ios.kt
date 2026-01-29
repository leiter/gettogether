@file:OptIn(
    kotlinx.cinterop.ExperimentalForeignApi::class,
    kotlinx.cinterop.BetaInteropApi::class,
    kotlin.io.encoding.ExperimentalEncodingApi::class
)

package com.gettogether.app.coil

import coil3.ImageLoader
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import com.gettogether.app.data.util.VCardParser
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.get
import kotlinx.cinterop.reinterpret
import okio.Buffer
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSLog

/**
 * Custom Coil Fetcher for iOS that loads avatar images from Jami daemon's vCard files.
 *
 * vCard files contain profile data including base64-encoded photos.
 * This fetcher parses the vCard and extracts the photo data directly.
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
        NSLog("$TAG: Fetching avatar from vCard: $vcardPath")

        val fileManager = NSFileManager.defaultManager
        if (!fileManager.fileExistsAtPath(vcardPath)) {
            NSLog("$TAG: vCard file not found: $vcardPath")
            // Diagnostic: log parent and grandparent directory existence
            val parentDir = vcardPath.substringBeforeLast("/")
            val grandparentDir = parentDir.substringBeforeLast("/")
            val parentExists = fileManager.fileExistsAtPath(parentDir)
            val grandparentExists = fileManager.fileExistsAtPath(grandparentDir)
            NSLog("$TAG:   parent dir ($parentDir) exists=$parentExists")
            NSLog("$TAG:   grandparent dir ($grandparentDir) exists=$grandparentExists")
            if (parentExists) {
                val contents = fileManager.contentsOfDirectoryAtPath(parentDir, null)
                NSLog("$TAG:   parent dir contents: $contents")
            }
            return null
        }

        return try {
            // Read vCard file
            val nsData = fileManager.contentsAtPath(vcardPath)
            if (nsData == null || nsData.length.toInt() == 0) {
                NSLog("$TAG: vCard file is empty: $vcardPath")
                return null
            }

            // Convert NSData to ByteArray
            val vcardBytes = nsDataToByteArray(nsData)
            if (vcardBytes.isEmpty()) {
                NSLog("$TAG: Failed to read vCard bytes")
                return null
            }

            // Parse vCard
            val profile = VCardParser.parse(vcardBytes)
            if (profile == null) {
                NSLog("$TAG: Failed to parse vCard: $vcardPath")
                return null
            }

            val photoBase64 = profile.photoBase64
            if (photoBase64.isNullOrEmpty()) {
                NSLog("$TAG: No photo found in vCard: $vcardPath")
                return null
            }

            // Decode base64 to bytes
            val imageBytes = decodeBase64(photoBase64)
            if (imageBytes.isEmpty()) {
                NSLog("$TAG: Decoded photo is empty")
                return null
            }

            NSLog("$TAG: Extracted avatar from vCard (${imageBytes.size} bytes)")

            // Create ImageSource from bytes
            val buffer = Buffer().write(imageBytes)
            val imageSource = ImageSource(
                source = buffer,
                fileSystem = okio.FileSystem.SYSTEM,
            )

            SourceFetchResult(
                source = imageSource,
                mimeType = "image/jpeg",
                // Use NETWORK instead of DISK to prevent Coil from serving stale
                // cached avatars when the vCard content changes (e.g. display name update)
                dataSource = DataSource.NETWORK
            )
        } catch (e: Exception) {
            NSLog("$TAG: Failed to extract avatar from vCard: ${e.message}")
            null
        }
    }

    private fun nsDataToByteArray(nsData: NSData): ByteArray {
        val size = nsData.length.toInt()
        if (size == 0 || nsData.bytes == null) return ByteArray(0)

        val ptr = nsData.bytes!!.reinterpret<ByteVar>()
        return ByteArray(size) { ptr[it] }
    }

    private fun decodeBase64(base64: String): ByteArray {
        return try {
            // Use kotlin.io.encoding.Base64 for decoding
            kotlin.io.encoding.Base64.decode(base64)
        } catch (e: Exception) {
            NSLog("$TAG: Base64 decode failed: ${e.message}")
            ByteArray(0)
        }
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
