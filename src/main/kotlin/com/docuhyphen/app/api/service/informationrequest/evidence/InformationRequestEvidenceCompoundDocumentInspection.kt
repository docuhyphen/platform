package com.docuhyphen.app.api.service.informationrequest.evidence

import com.docuhyphen.app.api.model.informationrequest.evidence.InformationRequestEvidenceInspectionFacts
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

class InformationRequestEvidenceCompoundDocumentInspection {
    fun recognizes(header: ByteArray): Boolean =
        header.size >= SIGNATURE.size && SIGNATURE.indices.all { header[it] == SIGNATURE[it] }

    fun inspect(file: File, detected: String): InformationRequestEvidenceInspectionFacts {
        val readable = try {
            RandomAccessFile(file, "r").use { input ->
                CompoundFile(input).let { compound -> compound.encrypted() to compound.mediaType(detected) }
            }
        } catch (_: IOException) {
            null
        } catch (_: RuntimeException) {
            null
        }
        return InformationRequestEvidenceInspectionFacts(
            readable?.second ?: detected,
            pageCount = null,
            encrypted = readable?.first == true,
            corrupt = readable == null,
        )
    }

    private class Entry(
        val name: String,
        val type: Int,
        val start: Int,
        val size: Long,
    )

    private class CompoundFile(private val input: RandomAccessFile) {
        private val sectorSize: Int
        private val sectorCount: Int
        private val allocation: IntArray
        private val entries: List<Entry>
        private val miniAllocation: IntArray
        private val miniStream: IntArray

        init {
            val header = read(0, HEADER_SIZE)
            if (!SIGNATURE.indices.all { header.get(it) == SIGNATURE[it] }) throw IOException("Not a compound document")
            if (header.getShort(BYTE_ORDER_OFFSET) != BYTE_ORDER_MARK) throw IOException("Unknown byte order")
            val shift = header.getShort(SECTOR_SHIFT_OFFSET).toInt()
            if (shift != SMALL_SECTOR_SHIFT && shift != LARGE_SECTOR_SHIFT) throw IOException("Unknown sector size")
            if (header.getShort(MINI_SECTOR_SHIFT_OFFSET)
                    .toInt() != MINI_SECTOR_SHIFT
            ) throw IOException("Unknown mini sector size")
            sectorSize = 1 shl shift
            val sectors = (input.length() - sectorSize + sectorSize - 1) / sectorSize
            if (sectors <= 0 || sectors > Int.MAX_VALUE) throw IOException("No sectors follow the header")
            sectorCount = sectors.toInt()
            allocation = allocationTable(header)
            entries = directoryEntries(chain(header.getInt(FIRST_DIRECTORY_OFFSET)))
            val root = entries.firstOrNull()?.takeIf { it.type == ROOT_TYPE } ?: throw IOException("No root entry")
            val firstMiniSector = header.getInt(FIRST_MINI_ALLOCATION_OFFSET)
            miniAllocation = if (firstMiniSector == END_OF_CHAIN) IntArray(0) else table(chain(firstMiniSector))
            miniStream = if (root.size == 0L) IntArray(0) else chain(root.start)
            if (miniStream.size.toLong() * sectorSize < root.size) throw IOException("The mini stream is shorter than stated")
        }

        fun mediaType(detected: String): String {
            if (detected != GENERIC_OFFICE_TYPE) return detected
            val streams = entries.filter { it.type == STREAM_TYPE }.map { it.name }.toSet()
            return LEGACY_OFFICE_TYPES.entries.firstOrNull { (stream, _) -> stream in streams }?.value ?: detected
        }

        fun encrypted(): Boolean {
            val streams = entries.filter { it.type == STREAM_TYPE }.associateBy { it.name }
            if (ENCRYPTION_STREAMS.any { it in streams }) return true
            streams[WORD_STREAM]?.let { stream ->
                if (prefix(stream, WORD_HEADER_BYTES).getShort(WORD_FLAGS_OFFSET)
                        .toInt() and WORD_ENCRYPTED_FLAG != 0
                ) return true
            }
            (streams[WORKBOOK_STREAM] ?: streams[LEGACY_WORKBOOK_STREAM])?.let { stream ->
                if (workbookPassword(stream)) return true
            }
            streams[CURRENT_USER_STREAM]?.let { stream ->
                if (prefix(
                        stream,
                        CURRENT_USER_BYTES
                    ).getInt(CURRENT_USER_TOKEN_OFFSET) == ENCRYPTED_PRESENTATION_TOKEN
                ) return true
            }
            return false
        }

        private fun workbookPassword(stream: Entry): Boolean {
            val records = prefix(stream, minOf(stream.size, WORKBOOK_SCAN_BYTES.toLong()).toInt())
            var position = 0
            while (position + RECORD_HEADER_BYTES <= records.capacity()) {
                val type = records.getShort(position).toInt() and 0xFFFF
                if (type == PASSWORD_RECORD) return true
                if (type == END_OF_GLOBALS_RECORD) return false
                position += RECORD_HEADER_BYTES + (records.getShort(position + 2).toInt() and 0xFFFF)
            }
            return false
        }

        private fun allocationTable(header: ByteBuffer): IntArray {
            val count = header.getInt(ALLOCATION_SECTOR_COUNT_OFFSET)
            if (count <= 0 || count > sectorCount) throw IOException("The allocation table size is out of range")
            val locations = ArrayList<Int>(count)
            for (index in 0 until minOf(count, HEADER_ALLOCATION_ENTRIES)) {
                locations += header.getInt(HEADER_ALLOCATION_OFFSET + index * 4)
            }
            val perSector = sectorSize / 4 - 1
            var next = header.getInt(FIRST_EXTENSION_OFFSET)
            var steps = 0
            while (locations.size < count) {
                steps += 1
                if (next < 0 || next >= sectorCount || steps > sectorCount) throw IOException("The allocation index is broken")
                val extension = sector(next)
                for (index in 0 until perSector) {
                    if (locations.size < count) locations += extension.getInt(index * 4)
                }
                next = extension.getInt(perSector * 4)
            }
            return table(locations.toIntArray())
        }

        private fun table(locations: IntArray): IntArray {
            val perSector = sectorSize / 4
            val values = IntArray(locations.size * perSector)
            locations.forEachIndexed { position, location ->
                if (location < 0 || location >= sectorCount) throw IOException("An allocation sector is out of range")
                val sector = sector(location)
                for (index in 0 until perSector) values[position * perSector + index] = sector.getInt(index * 4)
            }
            return values
        }

        private fun chain(start: Int): IntArray {
            val sectors = ArrayList<Int>()
            var current = start
            while (current != END_OF_CHAIN) {
                if (current < 0 || current >= sectorCount || current >= allocation.size || sectors.size >= sectorCount) {
                    throw IOException("A sector chain is broken")
                }
                sectors += current
                current = allocation[current]
            }
            return sectors.toIntArray()
        }

        private fun directoryEntries(directory: IntArray): List<Entry> {
            val found = ArrayList<Entry>()
            directory.forEach { location ->
                val sector = sector(location)
                for (offset in 0 until sectorSize step ENTRY_SIZE) {
                    val type = sector.get(offset + ENTRY_TYPE_OFFSET).toInt()
                    if (type !in ENTRY_TYPES) throw IOException("A directory entry has an unknown type")
                    if (type == UNUSED_TYPE) continue
                    val nameLength = sector.getShort(offset + NAME_LENGTH_OFFSET).toInt()
                    if (nameLength < 2 || nameLength > NAME_BYTES) throw IOException("A directory entry name is malformed")
                    val name = ByteArray((nameLength - 2) / 2 * 2)
                    sector.get(offset, name)
                    val stated = sector.getLong(offset + ENTRY_SIZE_OFFSET)
                    val size = if (sectorSize == 1 shl SMALL_SECTOR_SHIFT) stated and 0xFFFFFFFFL else stated
                    if (size < 0) throw IOException("A directory entry size is out of range")
                    found += Entry(
                        name = String(name, Charsets.UTF_16LE),
                        type = type,
                        start = sector.getInt(offset + ENTRY_START_OFFSET),
                        size = size,
                    )
                }
            }
            return found
        }

        private fun prefix(stream: Entry, length: Int): ByteBuffer {
            if (stream.size < length) throw IOException("A stream is shorter than its records")
            val bytes = ByteArray(length)
            if (stream.size < MINI_STREAM_CUTOFF) readMini(stream.start, bytes) else readRegular(stream.start, bytes)
            return ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        }

        private fun readRegular(start: Int, target: ByteArray) {
            var copied = 0
            var current = start
            var steps = 0
            while (copied < target.size) {
                steps += 1
                if (current < 0 || current >= sectorCount || current >= allocation.size || steps > sectorCount) {
                    throw IOException("A stream chain is broken")
                }
                val count = minOf(sectorSize, target.size - copied)
                readInto(offsetOf(current), target, copied, count)
                copied += count
                current = allocation[current]
            }
        }

        private fun readMini(start: Int, target: ByteArray) {
            var copied = 0
            var current = start
            var steps = 0
            while (copied < target.size) {
                steps += 1
                if (current < 0 || current >= miniAllocation.size || steps > miniAllocation.size) {
                    throw IOException("A mini stream chain is broken")
                }
                val position = current.toLong() * MINI_SECTOR_SIZE
                val sectorIndex = (position / sectorSize).toInt()
                if (sectorIndex >= miniStream.size) throw IOException("A mini sector lies outside the mini stream")
                val count = minOf(MINI_SECTOR_SIZE, target.size - copied)
                readInto(offsetOf(miniStream[sectorIndex]) + position % sectorSize, target, copied, count)
                copied += count
                current = miniAllocation[current]
            }
        }

        private fun sector(location: Int): ByteBuffer = read(offsetOf(location), sectorSize)

        private fun offsetOf(location: Int): Long = (location.toLong() + 1) * sectorSize

        private fun read(position: Long, length: Int): ByteBuffer {
            val bytes = ByteArray(length)
            readInto(position, bytes, 0, length)
            return ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        }

        private fun readInto(position: Long, target: ByteArray, offset: Int, length: Int) {
            input.seek(position)
            input.readFully(target, offset, length)
        }
    }

    private companion object {
        val SIGNATURE = byteArrayOf(
            0xD0.toByte(), 0xCF.toByte(), 0x11, 0xE0.toByte(), 0xA1.toByte(), 0xB1.toByte(), 0x1A, 0xE1.toByte(),
        )
        val ENCRYPTION_STREAMS = setOf("EncryptionInfo", "EncryptedPackage", "EncryptedSummary")
        val ENTRY_TYPES = setOf(0, 1, 2, 5)
        const val HEADER_SIZE = 512
        const val BYTE_ORDER_OFFSET = 0x1C
        const val BYTE_ORDER_MARK: Short = -2
        const val SECTOR_SHIFT_OFFSET = 0x1E
        const val MINI_SECTOR_SHIFT_OFFSET = 0x20
        const val ALLOCATION_SECTOR_COUNT_OFFSET = 0x2C
        const val FIRST_DIRECTORY_OFFSET = 0x30
        const val FIRST_MINI_ALLOCATION_OFFSET = 0x3C
        const val FIRST_EXTENSION_OFFSET = 0x44
        const val HEADER_ALLOCATION_OFFSET = 0x4C
        const val HEADER_ALLOCATION_ENTRIES = 109
        const val SMALL_SECTOR_SHIFT = 9
        const val LARGE_SECTOR_SHIFT = 12
        const val MINI_SECTOR_SHIFT = 6
        const val MINI_SECTOR_SIZE = 64
        const val MINI_STREAM_CUTOFF = 4_096L
        const val END_OF_CHAIN = -2
        const val ENTRY_SIZE = 128
        const val NAME_BYTES = 64
        const val NAME_LENGTH_OFFSET = 0x40
        const val ENTRY_TYPE_OFFSET = 0x42
        const val ENTRY_START_OFFSET = 0x74
        const val ENTRY_SIZE_OFFSET = 0x78
        const val UNUSED_TYPE = 0
        const val STREAM_TYPE = 2
        const val ROOT_TYPE = 5
        const val WORD_STREAM = "WordDocument"
        const val WORD_HEADER_BYTES = 12
        const val WORD_FLAGS_OFFSET = 10
        const val WORD_ENCRYPTED_FLAG = 0x0100
        const val WORKBOOK_STREAM = "Workbook"
        const val LEGACY_WORKBOOK_STREAM = "Book"
        const val WORKBOOK_SCAN_BYTES = 65_536
        const val RECORD_HEADER_BYTES = 4
        const val PASSWORD_RECORD = 0x002F
        const val END_OF_GLOBALS_RECORD = 0x000A
        const val CURRENT_USER_STREAM = "Current User"
        const val CURRENT_USER_BYTES = 16
        const val CURRENT_USER_TOKEN_OFFSET = 12
        const val ENCRYPTED_PRESENTATION_TOKEN = 0xF3D1C4DF.toInt()
        const val GENERIC_OFFICE_TYPE = "application/x-tika-msoffice"
        val LEGACY_OFFICE_TYPES = linkedMapOf(
            WORD_STREAM to "application/msword",
            WORKBOOK_STREAM to "application/vnd.ms-excel",
            LEGACY_WORKBOOK_STREAM to "application/vnd.ms-excel",
            "PowerPoint Document" to "application/vnd.ms-powerpoint",
        )
    }
}
