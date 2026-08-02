package com.docuhyphen.app.api.resource.audit

import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class ObsoleteDocumentAuditRouteRemovalTest
{
    @Test
    fun `obsolete unauthorised document audit resource is absent`()
    {
        assertThrows(ClassNotFoundException::class.java) {
            Class.forName("com.docuhyphen.app.api.resource.exchange.ExchangeDocumentAuditResource")
        }
    }
}
