package com.docuhyphen.app.api.service.informationrequest.template

import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.template.InformationRequestTemplateVersionContent
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.command.CommandRequestFingerprint
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import java.util.*

class InformationRequestConfigurationBundleServiceTest {
    private val access = RequestAccessContext(
        PrincipalRef.user(UUID.randomUUID()),
        AuthorizationContext(activeOrgId = UUID.randomUUID())
    )
    private val authoring = mock<InformationRequestTemplateAuthoringService>()
    private val service =
        InformationRequestConfigurationBundleService(InformationRequestConfigurationBundleValidator(), authoring)
    private val recordedHash = CommandRequestFingerprint.sha256Hex("record-collection@3")

    @Test
    fun `a bundle is checked against the Template Versions the caller can see`() {
        whenever(authoring.frozenVersionContents(any(), any())).thenReturn(
            listOf(InformationRequestTemplateVersionContent("record-collection", 3, recordedHash)),
        )

        val matching = service.check(bundle(recordedHash), access)
        val changed = service.check(bundle(CommandRequestFingerprint.sha256Hex("changed")), access)

        assertEquals(emptyList<Any>(), matching)
        assertEquals(
            listOf("templateVersions[0].contentHashSha256:hash.mismatch"),
            changed.map { "${it.path}:${it.code}" })
        verify(authoring, times(2)).frozenVersionContents(access, setOf("record-collection"))
    }

    @Test
    fun `an unreadable bundle is refused without looking any Template Version up`() {
        assertEquals(listOf("bundle.unreadable"), service.check("{not json", access).map { it.code })
        verifyNoInteractions(authoring)
    }

    private fun bundle(hash: String) = """
        {"formatVersion":1,"bundleKey":"record-bundle","bundleVersion":1,"displayName":"Record bundle",
         "templateVersions":[{"templateKey":"record-collection","versionNumber":3,"contentHashSha256":"$hash"}]}
    """.trimIndent()
}
