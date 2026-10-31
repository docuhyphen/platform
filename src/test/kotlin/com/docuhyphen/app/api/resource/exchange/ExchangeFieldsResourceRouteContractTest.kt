package com.docuhyphen.app.api.resource.exchange

import com.docuhyphen.app.api.resource.exchange.operations.ExchangeFieldsResourceOperations
import jakarta.ws.rs.PATCH
import jakarta.ws.rs.PUT
import jakarta.ws.rs.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ExchangeFieldsResourceRouteContractTest
{
    private fun verbsRoutedTo(path: String): List<String> =
        ExchangeFieldsResourceOperations::class.java.declaredMethods
            .filter { it.getAnnotation(Path::class.java)?.value == path }
            .flatMap { method ->
                listOfNotNull(
                    method.getAnnotation(PATCH::class.java)?.let { "PATCH" },
                    method.getAnnotation(PUT::class.java)?.let { "PUT" },
                )
            }
            .sorted()

    @Test
    fun `Fields values are changed only through PATCH`()
    {
        assertEquals(listOf("PATCH"), verbsRoutedTo("/{id}/fields"))
    }
}
