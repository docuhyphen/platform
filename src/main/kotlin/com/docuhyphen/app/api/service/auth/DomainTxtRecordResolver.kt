package com.docuhyphen.app.api.service.auth

import jakarta.enterprise.context.ApplicationScoped
import java.util.Hashtable
import javax.naming.directory.InitialDirContext

interface DomainTxtRecordResolver
{
    fun resolve(recordName: String): Set<String>
}

@ApplicationScoped
class JndiDomainTxtRecordResolver : DomainTxtRecordResolver
{
    override fun resolve(recordName: String): Set<String>
    {
        val environment = Hashtable<String, String>().apply {
            put("java.naming.factory.initial", "com.sun.jndi.dns.DnsContextFactory")
            put("com.sun.jndi.dns.timeout.initial", "3000")
            put("com.sun.jndi.dns.timeout.retries", "1")
        }
        val context = InitialDirContext(environment)
        return try
        {
            val attribute = context.getAttributes(recordName, arrayOf("TXT")).get("TXT")
                ?: return emptySet()
            (0 until attribute.size()).map { index ->
                normalizeTxtValue(attribute.get(index).toString())
            }.toSet()
        }
        finally
        {
            context.close()
        }
    }

    private fun normalizeTxtValue(value: String): String
    {
        return value.replace(Regex("\\\"\\s*\\\""), "").removeSurrounding("\"")
    }
}
