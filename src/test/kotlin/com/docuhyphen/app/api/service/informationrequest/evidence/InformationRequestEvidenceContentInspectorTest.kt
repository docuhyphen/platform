package com.docuhyphen.app.api.service.informationrequest.evidence

import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.encryption.AccessPermission
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Path
import java.util.concurrent.*
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam

class InformationRequestEvidenceContentInspectorTest
{
    @TempDir
    lateinit var directory: Path

    private val inspector = InformationRequestEvidenceContentInspector()

    @Test
    fun `a readable document reports its detected type and page count`()
    {
        val facts = inspector.inspect(pdf("record.pdf", pages = 3))

        assertEquals("application/pdf", facts.detectedMediaType)
        assertEquals(3, facts.pageCount)
        assertFalse(facts.encrypted)
        assertFalse(facts.corrupt)
    }

    @Test
    fun `detection reads the bytes, never the declared file name`()
    {
        val facts = inspector.inspect(pdf("record.png", pages = 1))

        assertEquals("application/pdf", facts.detectedMediaType)
    }

    @Test
    fun `a document that needs a password to open is reported as encrypted with no page count`()
    {
        val file = directory.resolve("protected.pdf").toFile()
        PDDocument().use { document ->
            document.addPage(PDPage())
            document.protect(StandardProtectionPolicy("owner-secret", "user-secret", AccessPermission()).apply {
                encryptionKeyLength = 128
            })
            document.save(file)
        }

        val facts = inspector.inspect(file)

        assertTrue(facts.encrypted)
        assertFalse(facts.corrupt)
        assertNull(facts.pageCount)
    }

    @Test
    fun `a document that cannot be parsed is reported as corrupt`()
    {
        val file = directory.resolve("broken.pdf").toFile().apply { writeText("%PDF-1.7\nthis is not a document body") }

        val facts = inspector.inspect(file)

        assertEquals("application/pdf", facts.detectedMediaType)
        assertTrue(facts.corrupt)
        assertNull(facts.pageCount)
    }

    @Test
    fun `an image reports its detected type and no page count`()
    {
        val facts = inspector.inspect(write("image.bin", image("png")))

        assertEquals("image/png", facts.detectedMediaType)
        assertNull(facts.pageCount)
        assertFalse(facts.encrypted)
        assertFalse(facts.corrupt)
    }

    @ParameterizedTest
    @CsvSource("png, image/png", "jpg, image/jpeg", "gif, image/gif")
    fun `a whole image decodes and is readable`(format: String, mediaType: String) {
        val facts = inspector.inspect(write("whole.$format", image(format)))

        assertEquals(mediaType, facts.detectedMediaType)
        assertFalse(facts.corrupt)
    }

    @ParameterizedTest
    @CsvSource("png", "jpg")
    fun `an image cut short is reported as corrupt`(format: String) {
        val whole = image(format)

        val facts = inspector.inspect(write("cut.$format", whole.copyOf(whole.size * 2 / 3)))

        assertTrue(facts.corrupt)
        assertFalse(facts.encrypted)
    }

    @Test
    fun `an image whose data cannot be decoded is reported as corrupt`() {
        val signature = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)

        val facts = inspector.inspect(write("damaged.png", signature + ByteArray(512) { (it * 31).toByte() }))

        assertEquals("image/png", facts.detectedMediaType)
        assertTrue(facts.corrupt)
    }

    @Test
    fun `an image larger than the inspection bound counts as unreadable`() {
        val facts = inspector.inspect(write("vast.png", oversizedPngHeader()))

        assertTrue(facts.corrupt)
    }

    @Test
    fun `a progressive image larger than the progressive bound counts as unreadable while the same picture stored line by line is read`() {
        val bounded = InformationRequestEvidenceImageInspection(maximumProgressivePixels = 1_000)

        assertTrue(bounded.inspect(write("progressive.jpg", jpeg(progressive = true)), "image/jpeg").corrupt)
        assertFalse(bounded.inspect(write("sequential.jpg", jpeg(progressive = false)), "image/jpeg").corrupt)
        assertFalse(inspector.inspect(write("small-progressive.jpg", jpeg(progressive = true))).corrupt)
    }

    @Test
    fun `an image waits for the same inspection place as a document and gives it back when it finishes`() {
        val places = Semaphore(0, true)
        val waiting = InformationRequestEvidenceContentInspector(inspections = places)
        val image = write("waiting.png", image("png"))
        val executor = Executors.newSingleThreadExecutor()
        try {
            val inspection = executor.submit(Callable { waiting.inspect(image) })
            assertThrows(TimeoutException::class.java) { inspection.get(300, TimeUnit.MILLISECONDS) }

            places.release()

            assertFalse(inspection.get(10, TimeUnit.SECONDS).corrupt)
            assertEquals(1, places.availablePermits())
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun `an office document sealed with a password is reported as encrypted`() {
        val file = write(
            "sealed.docx",
            CompoundFiles.of(
                "EncryptionInfo" to ByteArray(240) { 1.toByte() },
                "EncryptedPackage" to ByteArray(6_000) { 2.toByte() },
            ),
        )

        val facts = inspector.inspect(file)

        assertEquals("application/x-tika-msoffice", facts.detectedMediaType)
        assertTrue(facts.encrypted)
        assertFalse(facts.corrupt)
        assertNull(facts.pageCount)
    }

    @Test
    fun `a legacy word processing document that states it is encrypted is reported as encrypted`() {
        val file = write("sealed.doc", CompoundFiles.of("WordDocument" to wordDocument(encrypted = true)))

        val facts = inspector.inspect(file)

        assertTrue(facts.encrypted)
        assertFalse(facts.corrupt)
    }

    @Test
    fun `a legacy workbook that carries a password record is reported as encrypted`() {
        val file = write("sealed.xls", CompoundFiles.of("Workbook" to workbook(passwordRecord = true)))

        val facts = inspector.inspect(file)

        assertTrue(facts.encrypted)
        assertFalse(facts.corrupt)
    }

    @Test
    fun `a legacy presentation whose current user record states encryption is reported as encrypted`() {
        val file = write(
            "sealed.ppt",
            CompoundFiles.of(
                "Current User" to currentUser(encrypted = true),
                "PowerPoint Document" to ByteArray(4_200) { 3.toByte() },
            ),
        )

        val facts = inspector.inspect(file)

        assertTrue(facts.encrypted)
        assertFalse(facts.corrupt)
    }

    @Test
    fun `a compound document with no encryption marker is readable`() {
        val file = write(
            "open.doc",
            CompoundFiles.of(
                "WordDocument" to wordDocument(encrypted = false),
                "Workbook" to workbook(passwordRecord = false),
                "Current User" to currentUser(encrypted = false),
            ),
        )

        val facts = inspector.inspect(file)

        assertEquals("application/msword", facts.detectedMediaType)
        assertFalse(facts.encrypted)
        assertFalse(facts.corrupt)
    }

    @Test
    fun `a compound document whose structure cannot be read is reported as corrupt`() {
        val complete = CompoundFiles.of("WordDocument" to wordDocument(encrypted = false))
        val truncated = write("truncated.doc", complete.copyOf(700))
        val scrambled = write("scrambled.doc", complete.copyOf(512) + ByteArray(2_048) { 0x7F.toByte() })

        assertTrue(inspector.inspect(truncated).corrupt)
        assertTrue(inspector.inspect(scrambled).corrupt)
        assertFalse(inspector.inspect(truncated).encrypted)
    }

    @Test
    fun `a compound document whose stream states a negative size is reported as corrupt`() {
        val facts = inspector.inspect(write("negative.xls", negativeStreamCompoundDocument()))

        assertTrue(facts.corrupt)
        assertFalse(facts.encrypted)
    }

    @Test
    fun `an archive whose extended directory size and offset overflow together is reported as corrupt`() {
        val facts = inspector.inspect(write("overflow.zip", overflowingExtendedArchive(0x400000007FFFFFFFL)))

        assertTrue(facts.corrupt)
        assertFalse(facts.encrypted)
    }

    @Test
    fun `an archive whose overflowing extended directory names an allocatable size is reported as corrupt`() {
        val facts = inspector.inspect(write("allocatable.zip", overflowingExtendedArchive(0x400000007FFFFFF0L)))

        assertTrue(facts.corrupt)
        assertFalse(facts.encrypted)
    }

    @Test
    fun `an archive whose central directory is larger than the inspection bound is reported as corrupt`() {
        val bounded = InformationRequestEvidenceContentInspector(
            InformationRequestEvidenceArchiveInspection(maximumDirectoryBytes = 64),
        )
        val file = write("listed.zip", archive("first.txt" to "first record", "second.txt" to "second record"))

        assertTrue(bounded.inspect(file).corrupt)
        assertFalse(inspector.inspect(file).corrupt)
    }

    @ParameterizedTest
    @CsvSource(
        "word/document.xml, true, application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "xl/workbook.xml, true, application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        "ppt/presentation.xml, true, application/vnd.openxmlformats-officedocument.presentationml.presentation",
        "word/document.xml, false, application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "xl/workbook.xml, false, application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        "customXml/item1.xml, true, application/x-tika-ooxml",
    )
    fun `an office document archive is identified by the parts it lists`(
        part: String,
        typesFirst: Boolean,
        mediaType: String
    ) {
        val parts = listOf("[Content_Types].xml" to "<Types/>", "_rels/.rels" to "<Relationships/>", part to "<part/>")
        val ordered = if (typesFirst) parts else parts.reversed()

        val facts = inspector.inspect(write("office.bin", archive(*ordered.toTypedArray())))

        assertEquals(mediaType, facts.detectedMediaType)
        assertFalse(facts.encrypted)
        assertFalse(facts.corrupt)
    }

    @Test
    fun `an archive that lists office parts without the office package manifest stays an archive`() {
        val facts = inspector.inspect(
            write(
                "parts.zip",
                archive("word/document.xml" to "<part/>", "xl/workbook.xml" to "<part/>")
            )
        )

        assertEquals("application/zip", facts.detectedMediaType)
    }

    @Test
    fun `a readable archive is neither encrypted nor corrupt`() {
        val file = write("records.zip", archive("first.txt" to "first record", "second.txt" to "second record"))

        val facts = inspector.inspect(file)

        assertEquals("application/zip", facts.detectedMediaType)
        assertFalse(facts.encrypted)
        assertFalse(facts.corrupt)
    }

    @Test
    fun `an archive with a password-protected entry is reported as encrypted`() {
        val file = write("sealed.zip", markedEncrypted(archive("first.txt" to "first record")))

        val facts = inspector.inspect(file)

        assertTrue(facts.encrypted)
        assertFalse(facts.corrupt)
    }

    @Test
    fun `an OpenDocument file saved with a password is reported as encrypted and an open one is readable`() {
        val archives = InformationRequestEvidenceArchiveInspection()

        val sealed = archives.inspect(write("sealed.odt", openDocument(encrypted = true)), OPEN_DOCUMENT_TEXT)
        val open = archives.inspect(write("open.odt", openDocument(encrypted = false)), OPEN_DOCUMENT_TEXT)

        assertTrue(sealed.encrypted)
        assertFalse(sealed.corrupt)
        assertFalse(open.encrypted)
        assertFalse(open.corrupt)
    }

    @Test
    fun `an archive whose stored bytes no longer match their checksum is reported as corrupt`() {
        val bytes = storedArchive("first.txt", "first record that will be altered")
        val altered = bytes.copyOf().apply {
            val start = indexOf("first record".toByteArray())
            this[start] = 'F'.code.toByte()
        }

        val facts = inspector.inspect(write("altered.zip", altered))

        assertTrue(facts.corrupt)
        assertFalse(facts.encrypted)
    }

    @Test
    fun `an archive cut short is reported as corrupt`() {
        val bytes = archive("first.txt" to "first record ".repeat(200), "second.txt" to "second record ".repeat(200))

        val facts = inspector.inspect(write("cut.zip", bytes.copyOf(bytes.size / 2)))

        assertTrue(facts.corrupt)
    }

    @Test
    fun `an archive that expands past the inspection bounds is reported as corrupt`() {
        val bounded = InformationRequestEvidenceContentInspector(
            InformationRequestEvidenceArchiveInspection(maximumEntries = 2, maximumExpandedBytes = 1_024),
        )

        val manyEntries = write("many.zip", archive("a.txt" to "a", "b.txt" to "b", "c.txt" to "c"))
        val largeExpansion = write("large.zip", archive("large.txt" to "0".repeat(4_096)))
        val withinBounds = write("small.zip", archive("a.txt" to "a", "b.txt" to "b"))

        assertTrue(bounded.inspect(manyEntries).corrupt)
        assertTrue(bounded.inspect(largeExpansion).corrupt)
        assertFalse(bounded.inspect(withinBounds).corrupt)
    }

    private fun write(name: String, bytes: ByteArray): File =
        directory.resolve(name).toFile().apply { writeBytes(bytes) }

    private fun overflowingExtendedArchive(directorySize: Long): ByteArray {
        val extendedRecord = 8
        val locator = 64
        val endRecord = 84
        return ByteBuffer.allocate(106).order(ByteOrder.LITTLE_ENDIAN).apply {
            putInt(0, LOCAL_HEADER)
            putInt(extendedRecord, ZIP64_END_RECORD)
            putLong(extendedRecord + 32, 0L)
            putLong(extendedRecord + 40, directorySize)
            putLong(extendedRecord + 48, 0x4000000000000000L)
            putInt(locator, ZIP64_LOCATOR)
            putLong(locator + 8, extendedRecord.toLong())
            putInt(endRecord, END_RECORD)
            putShort(endRecord + 10, 0xFFFF.toShort())
            putInt(endRecord + 12, -1)
            putInt(endRecord + 16, -1)
        }.array()
    }

    private fun negativeStreamCompoundDocument(): ByteArray {
        val sector = 4_096
        val allocationSector = sector
        val directorySector = sector * 2
        val document = ByteBuffer.allocate(sector * 3).order(ByteOrder.LITTLE_ENDIAN)
        byteArrayOf(
            0xD0.toByte(), 0xCF.toByte(), 0x11, 0xE0.toByte(), 0xA1.toByte(), 0xB1.toByte(), 0x1A, 0xE1.toByte(),
        ).forEachIndexed { index, byte -> document.put(index, byte) }
        document.putShort(0x18, 0x003E).putShort(0x1A, 0x0004).putShort(0x1C, 0xFFFE.toShort())
        document.putShort(0x1E, 0x000C).putShort(0x20, 0x0006)
        document.putInt(0x2C, 1).putInt(0x30, 1).putInt(0x38, 4_096).putInt(0x3C, -2).putInt(0x44, -2)
        document.putInt(0x4C, 0)
        for (index in 1 until 109) document.putInt(0x4C + index * 4, -1)
        document.putInt(allocationSector, -3).putInt(allocationSector + 4, -2)
        for (index in 2 until sector / 4) document.putInt(allocationSector + index * 4, -1)
        directoryEntry(document, directorySector, "Root Entry", type = 5, start = -2, size = 0L)
        directoryEntry(document, directorySector + 128, "Workbook", type = 2, start = 0, size = -1L)
        return document.array()
    }

    private fun directoryEntry(document: ByteBuffer, base: Int, name: String, type: Int, start: Int, size: Long) {
        val encoded = name.toByteArray(Charsets.UTF_16LE)
        encoded.forEachIndexed { offset, byte -> document.put(base + offset, byte) }
        document.putShort(base + 0x40, (encoded.size + 2).toShort())
        document.put(base + 0x42, type.toByte())
        document.putInt(base + 0x74, start)
        document.putLong(base + 0x78, size)
    }

    private fun image(format: String): ByteArray {
        val picture = BufferedImage(96, 64, BufferedImage.TYPE_INT_RGB)
        for (x in 0 until picture.width) {
            for (y in 0 until picture.height) picture.setRGB(
                x,
                y,
                (x * 2 shl 16) or (y * 3 shl 8) or ((x xor y) and 0xFF)
            )
        }
        return ByteArrayOutputStream().also { ImageIO.write(picture, format, it) }.toByteArray()
    }

    private fun oversizedPngHeader(): ByteArray {
        val header = ByteBuffer.allocate(13).order(ByteOrder.BIG_ENDIAN)
            .putInt(20_000)
            .putInt(20_000)
            .put(8)
            .put(2)
            .put(0)
            .put(0)
            .put(0)
            .array()
        val chunk = "IHDR".toByteArray(Charsets.US_ASCII) + header
        val crc = CRC32().apply { update(chunk) }.value.toInt()
        return byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A) +
                ByteBuffer.allocate(4).putInt(header.size).array() +
                chunk +
                ByteBuffer.allocate(4).putInt(crc).array()
    }

    private fun archive(vararg entries: Pair<String, String>): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            entries.forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray())
                zip.closeEntry()
            }
        }
        return output.toByteArray()
    }

    private fun storedArchive(name: String, content: String): ByteArray {
        val bytes = content.toByteArray()
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(
                ZipEntry(name).apply {
                    method = ZipEntry.STORED
                    size = bytes.size.toLong()
                    compressedSize = bytes.size.toLong()
                    crc = CRC32().apply { update(bytes) }.value
                },
            )
            zip.write(bytes)
            zip.closeEntry()
        }
        return output.toByteArray()
    }

    private fun openDocument(encrypted: Boolean): ByteArray {
        val encryption = if (encrypted) "<manifest:encryption-data manifest:checksum-type=\"SHA1/1K\"/>" else ""
        val manifest = "<manifest:manifest xmlns:manifest=\"urn:oasis:names:tc:opendocument:xmlns:manifest:1.0\">" +
                "<manifest:file-entry manifest:full-path=\"content.xml\" manifest:media-type=\"text/xml\">$encryption" +
                "</manifest:file-entry></manifest:manifest>"
        val type = OPEN_DOCUMENT_TEXT.toByteArray()
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(
                ZipEntry("mimetype").apply {
                    method = ZipEntry.STORED
                    size = type.size.toLong()
                    compressedSize = type.size.toLong()
                    crc = CRC32().apply { update(type) }.value
                },
            )
            zip.write(type)
            zip.closeEntry()
            listOf(
                "META-INF/manifest.xml" to manifest,
                "content.xml" to "<office:document-content/>"
            ).forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray())
                zip.closeEntry()
            }
        }
        return output.toByteArray()
    }

    private fun jpeg(progressive: Boolean): ByteArray {
        val picture = ImageIO.read(image("png").inputStream())
        val writer = ImageIO.getImageWritersByFormatName("jpeg").next()
        val output = ByteArrayOutputStream()
        try {
            ImageIO.createImageOutputStream(output).use { stream ->
                writer.output = stream
                val parameters = writer.defaultWriteParam.apply {
                    progressiveMode = if (progressive) ImageWriteParam.MODE_DEFAULT else ImageWriteParam.MODE_DISABLED
                }
                writer.write(null, IIOImage(picture, null, null), parameters)
            }
        } finally {
            writer.dispose()
        }
        return output.toByteArray()
    }

    private fun markedEncrypted(bytes: ByteArray): ByteArray {
        val marked = bytes.copyOf()
        val buffer = ByteBuffer.wrap(marked).order(ByteOrder.LITTLE_ENDIAN)
        for (offset in 0..marked.size - 4) {
            when (buffer.getInt(offset)) {
                LOCAL_HEADER -> buffer.putShort(offset + 6, (buffer.getShort(offset + 6).toInt() or 1).toShort())
                CENTRAL_HEADER -> buffer.putShort(offset + 8, (buffer.getShort(offset + 8).toInt() or 1).toShort())
            }
        }
        return marked
    }

    private fun ByteArray.indexOf(target: ByteArray): Int =
        (0..size - target.size).first { start -> target.indices.all { this[start + it] == target[it] } }

    private fun wordDocument(encrypted: Boolean): ByteArray =
        ByteArray(5_000).also { stream ->
            ByteBuffer.wrap(stream).order(ByteOrder.LITTLE_ENDIAN)
                .putShort(0, 0xA5EC.toShort())
                .putShort(2, 0x00C1)
                .putShort(10, (if (encrypted) 0x0100 else 0x0000).toShort())
        }

    private fun workbook(passwordRecord: Boolean): ByteArray {
        val records = ByteBuffer.allocate(64).order(ByteOrder.LITTLE_ENDIAN)
        records.putShort(0x0809).putShort(16).put(ByteArray(16))
        if (passwordRecord) records.putShort(0x002F).putShort(6).put(ByteArray(6))
        records.putShort(0x0042).putShort(2).putShort(1200)
        records.putShort(0x000A).putShort(0)
        return records.array().copyOf(records.position())
    }

    private fun currentUser(encrypted: Boolean): ByteArray =
        ByteArray(40).also { stream ->
            ByteBuffer.wrap(stream).order(ByteOrder.LITTLE_ENDIAN)
                .putShort(2, 0x0FF6)
                .putInt(4, 32)
                .putInt(8, 20)
                .putInt(12, if (encrypted) 0xF3D1C4DF.toInt() else 0xE391C05F.toInt())
        }

    private fun pdf(name: String, pages: Int): File
    {
        val file = directory.resolve(name).toFile()
        PDDocument().use { document ->
            repeat(pages) { document.addPage(PDPage()) }
            document.save(file)
        }
        return file
    }

    internal object CompoundFiles {
        private const val SECTOR = 512
        private const val MINI_SECTOR = 64
        private const val MINI_STREAM_CUTOFF = 4_096
        private const val FREE = -1
        private const val END_OF_CHAIN = -2
        private const val FAT_SECTOR = -3
        private const val NO_STREAM = -1
        private val SIGNATURE = byteArrayOf(
            0xD0.toByte(), 0xCF.toByte(), 0x11, 0xE0.toByte(), 0xA1.toByte(), 0xB1.toByte(), 0x1A, 0xE1.toByte(),
        )

        fun of(vararg streams: Pair<String, ByteArray>): ByteArray {
            val small = streams.filter { it.second.size < MINI_STREAM_CUTOFF }
            val large = streams.filter { it.second.size >= MINI_STREAM_CUTOFF }

            val miniFat = mutableListOf<Int>()
            val miniStream = ByteArrayOutputStream()
            val miniStart = mutableMapOf<String, Int>()
            small.forEach { (name, bytes) ->
                val sectors = maxOf(1, (bytes.size + MINI_SECTOR - 1) / MINI_SECTOR)
                val first = miniFat.size
                miniStart[name] = first
                repeat(sectors) { index -> miniFat += if (index == sectors - 1) END_OF_CHAIN else first + index + 1 }
                miniStream.write(bytes)
                miniStream.write(ByteArray(sectors * MINI_SECTOR - bytes.size))
            }
            val miniBytes = miniStream.toByteArray()

            val directorySectors = (streams.size + 1 + 3) / 4
            val miniFatSectors = (miniFat.size * 4 + SECTOR - 1) / SECTOR
            val miniStreamSectors = (miniBytes.size + SECTOR - 1) / SECTOR

            val fat = mutableListOf(FAT_SECTOR)
            val directoryStart = fat.size
            chain(fat, directorySectors)
            val miniFatStart = if (miniFatSectors > 0) fat.size else END_OF_CHAIN
            chain(fat, miniFatSectors)
            val miniStreamStart = if (miniStreamSectors > 0) fat.size else END_OF_CHAIN
            chain(fat, miniStreamSectors)
            val largeStart = large.associate { (name, bytes) ->
                val start = fat.size
                chain(fat, (bytes.size + SECTOR - 1) / SECTOR)
                name to start
            }
            check(fat.size <= SECTOR / 4) { "The synthetic compound file fits one allocation sector" }
            while (fat.size < SECTOR / 4) fat += FREE

            val directory = ByteBuffer.allocate(directorySectors * SECTOR).order(ByteOrder.LITTLE_ENDIAN)
            repeat(directorySectors * 4) { index -> entry(directory, index, "", 0, NO_STREAM, NO_STREAM, 0, 0L) }
            entry(
                directory,
                0,
                "Root Entry",
                5,
                if (streams.isEmpty()) NO_STREAM else 1,
                NO_STREAM,
                miniStreamStart,
                miniBytes.size.toLong(),
            )
            streams.forEachIndexed { index, (name, bytes) ->
                val start = miniStart[name] ?: largeStart.getValue(name)
                val right = if (index + 2 <= streams.size) index + 2 else NO_STREAM
                entry(directory, index + 1, name, 2, NO_STREAM, right, start, bytes.size.toLong())
            }

            val output = ByteArrayOutputStream()
            output.write(header(directoryStart, miniFatStart, miniFatSectors))
            output.write(ints(fat))
            output.write(directory.array())
            if (miniFatSectors > 0) {
                output.write(padded(ints(miniFat), miniFatSectors * SECTOR, 0xFF.toByte()))
            }
            output.write(padded(miniBytes, miniStreamSectors * SECTOR, 0))
            large.forEach { (_, bytes) ->
                output.write(
                    padded(
                        bytes,
                        ((bytes.size + SECTOR - 1) / SECTOR) * SECTOR,
                        0
                    )
                )
            }
            return output.toByteArray()
        }

        private fun chain(fat: MutableList<Int>, sectors: Int) {
            val first = fat.size
            repeat(sectors) { index -> fat += if (index == sectors - 1) END_OF_CHAIN else first + index + 1 }
        }

        private fun header(directoryStart: Int, miniFatStart: Int, miniFatSectors: Int): ByteArray {
            val header = ByteBuffer.allocate(SECTOR).order(ByteOrder.LITTLE_ENDIAN)
            header.put(SIGNATURE)
            header.position(0x18)
            header.putShort(0x003E).putShort(0x0003).putShort(0xFFFE.toShort()).putShort(0x0009).putShort(0x0006)
            header.position(0x28)
            header.putInt(0).putInt(1).putInt(directoryStart).putInt(0).putInt(MINI_STREAM_CUTOFF)
            header.putInt(miniFatStart).putInt(miniFatSectors).putInt(END_OF_CHAIN).putInt(0)
            header.putInt(0)
            repeat(108) { header.putInt(FREE) }
            return header.array()
        }

        @Suppress("LongParameterList")
        private fun entry(
            directory: ByteBuffer,
            index: Int,
            name: String,
            type: Int,
            child: Int,
            right: Int,
            start: Int,
            size: Long,
        ) {
            val base = index * 128
            val encoded = name.toByteArray(Charsets.UTF_16LE)
            repeat(64) { directory.put(base + it, 0) }
            encoded.forEachIndexed { offset, byte -> directory.put(base + offset, byte) }
            directory.putShort(base + 0x40, (if (name.isEmpty()) 0 else encoded.size + 2).toShort())
            directory.put(base + 0x42, type.toByte())
            directory.put(base + 0x43, 1)
            directory.putInt(base + 0x44, NO_STREAM)
            directory.putInt(base + 0x48, right)
            directory.putInt(base + 0x4C, child)
            directory.putInt(base + 0x74, start)
            directory.putLong(base + 0x78, size)
        }

        private fun ints(values: List<Int>): ByteArray =
            ByteBuffer.allocate(values.size * 4).order(ByteOrder.LITTLE_ENDIAN).apply { values.forEach { putInt(it) } }
                .array()

        private fun padded(bytes: ByteArray, length: Int, fill: Byte): ByteArray =
            ByteArray(length) { index -> if (index < bytes.size) bytes[index] else fill }
    }

    private companion object {
        const val LOCAL_HEADER = 0x04034B50
        const val CENTRAL_HEADER = 0x02014B50
        const val END_RECORD = 0x06054B50
        const val ZIP64_END_RECORD = 0x06064B50
        const val ZIP64_LOCATOR = 0x07064B50
        const val OPEN_DOCUMENT_TEXT = "application/vnd.oasis.opendocument.text"
    }
}
