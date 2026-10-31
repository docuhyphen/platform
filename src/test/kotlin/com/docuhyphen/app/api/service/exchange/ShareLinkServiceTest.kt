package com.docuhyphen.app.api.service.exchange

import com.docuhyphen.app.api.model.entity.ShareLink
import com.docuhyphen.app.api.repository.exchange.ShareLinkRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import java.util.*

class ShareLinkServiceTest {
    private val shareLinkRepository = mock<ShareLinkRepository>()
    private val service = ShareLinkService(shareLinkRepository)
    private val shareLink = ShareLink()

    @Test
    fun `a share link is read by id or token hash without a row lock`() {
        whenever(shareLinkRepository.findById(shareLink.id)).thenReturn(shareLink)
        whenever(shareLinkRepository.findByTokenHash("token-hash")).thenReturn(shareLink)

        assertSame(shareLink, service.findShareLink(shareLink.id))
        assertSame(shareLink, service.findByTokenHash("token-hash"))
        verify(shareLinkRepository, never()).findByIdForUpdate(any())
    }

    @Test
    fun `locking a share link takes its row lock by id`() {
        whenever(shareLinkRepository.findByIdForUpdate(shareLink.id)).thenReturn(shareLink)

        assertSame(shareLink, service.lockShareLinkForUpdate(shareLink.id))
        verify(shareLinkRepository).findByIdForUpdate(shareLink.id)
    }

    @Test
    fun `bootstrap links are read for the shares they are bound to`() {
        val shareId = UUID.randomUUID()
        whenever(shareLinkRepository.findBootstrapLinksForShares(setOf(shareId))).thenReturn(listOf(shareLink))
        whenever(shareLinkRepository.findActiveBootstrapLinksForShare(shareId)).thenReturn(listOf(shareLink))

        assertEquals(listOf(shareLink), service.bootstrapLinksForShares(setOf(shareId)))
        assertEquals(listOf(shareLink), service.activeBootstrapLinksForShare(shareId))
    }

    @Test
    fun `creating and updating a share link store it`() {
        whenever(shareLinkRepository.save(shareLink)).thenReturn(shareLink)
        whenever(shareLinkRepository.update(shareLink)).thenReturn(shareLink)

        assertSame(shareLink, service.create(shareLink))
        assertSame(shareLink, service.update(shareLink))
        verify(shareLinkRepository).save(shareLink)
        verify(shareLinkRepository).update(shareLink)
    }
}
