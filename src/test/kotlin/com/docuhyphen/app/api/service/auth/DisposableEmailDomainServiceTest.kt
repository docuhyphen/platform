package com.docuhyphen.app.api.service.auth

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DisposableEmailDomainServiceTest
{
    @Test
    fun `parses normalized domains and ignores comments and invalid entries`()
    {
        val domains = DisposableEmailDomainService.parseDomainList(
            """
            # generated list
            Mailinator.COM

            temporary.example.org
            invalid domain
            """.trimIndent()
        )

        assertTrue("mailinator.com" in domains)
        assertTrue("temporary.example.org" in domains)
        assertFalse("invalid domain" in domains)
    }

    @Test
    fun `matches listed domains and their subdomains`()
    {
        val domains = setOf("mailinator.com")

        assertTrue(DisposableEmailDomainService.isDisposableEmail("person@mailinator.com", domains))
        assertTrue(DisposableEmailDomainService.isDisposableEmail("person@inbox.mailinator.com", domains))
        assertFalse(DisposableEmailDomainService.isDisposableEmail("person@example.com", domains))
    }
}
