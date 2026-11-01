package com.docuhyphen.app.api.model.document

import com.docuhyphen.app.api.model.entity.DocumentVersion
import com.docuhyphen.app.api.model.entity.PrincipalKind
import com.docuhyphen.app.api.model.auth.authz.PrincipalRef
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.UUID

class DocumentVersionCreatorMapperTest
{
    @Test
    fun `a version is read as the principal that created it`()
    {
        val participantId = UUID.randomUUID()
        val version = DocumentVersion().apply {
            createdByPrincipalKind = PrincipalKind.PARTICIPANT
            createdByPrincipalId = participantId
        }

        assertEquals(PrincipalRef.participant(participantId), DocumentVersionCreatorMapper.read(version))
    }

    @Test
    fun `recording a creator states it on the version`()
    {
        val groupId = UUID.randomUUID()
        val version = DocumentVersion()

        DocumentVersionCreatorMapper.recordOn(version, PrincipalRef.group(groupId))

        assertEquals(PrincipalKind.PRINCIPAL_GROUP, version.createdByPrincipalKind)
        assertEquals(groupId, version.createdByPrincipalId)
        assertEquals(PrincipalRef.group(groupId), DocumentVersionCreatorMapper.read(version))
    }
}
