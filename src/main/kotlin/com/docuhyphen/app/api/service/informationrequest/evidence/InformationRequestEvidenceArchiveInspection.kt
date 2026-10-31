package com.docuhyphen.app.api.service.informationrequest.evidence

import com.docuhyphen.app.api.model.informationrequest.evidence.InformationRequestEvidenceInspectionFacts
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.ZipException
import java.util.zip.ZipInputStream

class InformationRequestEvidenceArchiveInspection(
    private val maximumEntries: Int = MAXIMUM_ENTRIES,
    private val maximumExpandedBytes: Long = MAXIMUM_EXPANDED_BYTES,
    private val maximumDirectoryBytes: Long = MAXIMUM_DIRECTORY_BYTES,
) {
    fun recognizes(header: ByteArray): Boolean =
        header.size >= SIGNATURE_BYTES &&
                header[0] == 'P'.code.toByte() &&
                header[1] == 'K'.code.toByte() &&
                ((header[2].toInt() == 3 && header[3].toInt() == 4) || (header[2].toInt() == 5 && header[3].toInt() == 6))

    fun inspect(file: File, detected: String): InformationRequestEvidenceInspectionFacts {
        val listing = readable { RandomAccessFile(file, "r").use { input -> listing(input) } }
            ?: return facts(detected, encrypted = false, corrupt = true)
        val identified = officePackageType(detected, listing.names)
        if (listing.flags.any { it and ENCRYPTED_FLAG != 0 }) {
            return facts(identified, encrypted = true, corrupt = false)
        }
        val readsManifest = identified.startsWith(OPEN_DOCUMENT_PREFIX)
        val encryptionDeclared = readable { expand(file, listing.flags.size, readsManifest) }
            ?: return facts(identified, encrypted = false, corrupt = true)
        return facts(identified, encrypted = encryptionDeclared, corrupt = false)
    }

    private fun officePackageType(detected: String, names: List<String>): String {
        if (detected != ARCHIVE_TYPE && detected != OFFICE_PACKAGE_TYPE) return detected
        if (OFFICE_PACKAGE_MANIFEST !in names) return detected
        val kinds = OFFICE_PART_TYPES.filterKeys { folder -> names.any { it.startsWith(folder) } }.values
        return kinds.singleOrNull() ?: OFFICE_PACKAGE_TYPE
    }

    private class Listing(val flags: List<Int>, val names: List<String>)

    private fun listing(input: RandomAccessFile): Listing {
        val length = input.length()
        val tailLength = minOf(length, (END_RECORD_SIZE + MAXIMUM_COMMENT_BYTES).toLong()).toInt()
        if (tailLength < END_RECORD_SIZE) throw ZipException("The archive is too short to state its contents")
        val tail = read(input, length - tailLength, tailLength)
        val end = (tailLength - END_RECORD_SIZE downTo 0).firstOrNull { tail.getInt(it) == END_SIGNATURE }
            ?: throw ZipException("The archive states no central directory")
        val endPosition = length - tailLength + end

        var entries = (tail.getShort(end + 10).toInt() and 0xFFFF).toLong()
        var directorySize = tail.getInt(end + 12).toLong() and 0xFFFFFFFFL
        var directoryOffset = tail.getInt(end + 16).toLong() and 0xFFFFFFFFL
        var boundary = endPosition
        if (entries == 0xFFFFL || directorySize == 0xFFFFFFFFL || directoryOffset == 0xFFFFFFFFL) {
            if (endPosition < ZIP64_LOCATOR_SIZE) throw ZipException("The archive states no extended central directory")
            val locator = read(input, endPosition - ZIP64_LOCATOR_SIZE, ZIP64_LOCATOR_SIZE)
            if (locator.getInt(0) != ZIP64_LOCATOR_SIGNATURE) throw ZipException("The extended directory locator is missing")
            val recordPosition = locator.getLong(8)
            if (recordPosition < 0 || recordPosition > endPosition - ZIP64_END_RECORD_SIZE) {
                throw ZipException("The extended directory record lies outside the archive")
            }
            val record = read(input, recordPosition, ZIP64_END_RECORD_SIZE)
            if (record.getInt(0) != ZIP64_END_SIGNATURE) throw ZipException("The extended directory record is missing")
            entries = record.getLong(32)
            directorySize = record.getLong(40)
            directoryOffset = record.getLong(48)
            boundary = recordPosition
        }
        if (entries < 0 || entries > maximumEntries) throw ZipException("The archive lists more entries than are inspected")
        if (directoryOffset < 0 || directorySize < 0 || directoryOffset > boundary || directorySize > boundary - directoryOffset) {
            throw ZipException("The central directory lies outside the archive")
        }
        if (directorySize > maximumDirectoryBytes) throw ZipException("The central directory is larger than is inspected")

        val directory = read(input, directoryOffset, directorySize.toInt())
        val flags = ArrayList<Int>(entries.toInt())
        val names = ArrayList<String>(entries.toInt())
        var position = 0
        repeat(entries.toInt()) {
            if (position + CENTRAL_HEADER_SIZE > directory.capacity() || directory.getInt(position) != CENTRAL_SIGNATURE) {
                throw ZipException("A central directory entry is malformed")
            }
            val nameLength = directory.getShort(position + 28).toInt() and 0xFFFF
            if (position + CENTRAL_HEADER_SIZE + nameLength > directory.capacity()) {
                throw ZipException("A central directory entry name is malformed")
            }
            flags += directory.getShort(position + 8).toInt() and 0xFFFF
            names += String(directory.array(), position + CENTRAL_HEADER_SIZE, nameLength, Charsets.ISO_8859_1)
            position += CENTRAL_HEADER_SIZE +
                    nameLength +
                    (directory.getShort(position + 30).toInt() and 0xFFFF) +
                    (directory.getShort(position + 32).toInt() and 0xFFFF)
        }
        return Listing(flags, names)
    }

    private fun expand(file: File, listed: Int, readsManifest: Boolean): Boolean {
        var entries = 0
        var expanded = 0L
        var encryptionDeclared = false
        val buffer = ByteArray(BUFFER_BYTES)
        ZipInputStream(file.inputStream().buffered()).use { archive ->
            while (true) {
                val entry = archive.nextEntry ?: break
                entries += 1
                if (entries > maximumEntries) throw ZipException("The archive holds more entries than are inspected")
                val manifest =
                    if (readsManifest && entry.name == OPEN_DOCUMENT_MANIFEST) ByteArrayOutputStream() else null
                while (true) {
                    val read = archive.read(buffer)
                    if (read < 0) break
                    expanded += read
                    if (expanded > maximumExpandedBytes) throw ZipException("The archive expands beyond the inspected size")
                    if (manifest != null && manifest.size() < MAXIMUM_MANIFEST_BYTES) manifest.write(buffer, 0, read)
                }
                if (manifest != null && manifest.toString(Charsets.UTF_8).contains(OPEN_DOCUMENT_ENCRYPTION)) {
                    encryptionDeclared = true
                }
            }
        }
        if (entries != listed) throw ZipException("The archive's entries do not match its central directory")
        return encryptionDeclared
    }

    private fun read(input: RandomAccessFile, position: Long, length: Int): ByteBuffer {
        val bytes = ByteArray(length)
        input.seek(position)
        input.readFully(bytes)
        return ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
    }

    private fun <T> readable(block: () -> T): T? =
        try {
            block()
        } catch (_: IOException) {
            null
        } catch (_: RuntimeException) {
            null
        }

    private fun facts(detected: String, encrypted: Boolean, corrupt: Boolean) =
        InformationRequestEvidenceInspectionFacts(detected, pageCount = null, encrypted = encrypted, corrupt = corrupt)

    private companion object {
        const val MAXIMUM_ENTRIES = 10_000
        const val MAXIMUM_EXPANDED_BYTES = 268_435_456L
        const val MAXIMUM_DIRECTORY_BYTES = 16_777_216L
        const val SIGNATURE_BYTES = 4
        const val END_SIGNATURE = 0x06054B50
        const val CENTRAL_SIGNATURE = 0x02014B50
        const val ZIP64_LOCATOR_SIGNATURE = 0x07064B50
        const val ZIP64_END_SIGNATURE = 0x06064B50
        const val END_RECORD_SIZE = 22
        const val CENTRAL_HEADER_SIZE = 46
        const val ZIP64_LOCATOR_SIZE = 20
        const val ZIP64_END_RECORD_SIZE = 56
        const val MAXIMUM_COMMENT_BYTES = 65_535
        const val ENCRYPTED_FLAG = 0x0001
        const val BUFFER_BYTES = 8_192
        const val ARCHIVE_TYPE = "application/zip"
        const val OFFICE_PACKAGE_TYPE = "application/x-tika-ooxml"
        const val OFFICE_PACKAGE_MANIFEST = "[Content_Types].xml"
        const val OPEN_DOCUMENT_PREFIX = "application/vnd.oasis.opendocument."
        const val OPEN_DOCUMENT_MANIFEST = "META-INF/manifest.xml"
        const val OPEN_DOCUMENT_ENCRYPTION = "encryption-data"
        const val MAXIMUM_MANIFEST_BYTES = 1_048_576
        val OFFICE_PART_TYPES = mapOf(
            "word/" to "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "xl/" to "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "ppt/" to "application/vnd.openxmlformats-officedocument.presentationml.presentation",
        )
    }
}
