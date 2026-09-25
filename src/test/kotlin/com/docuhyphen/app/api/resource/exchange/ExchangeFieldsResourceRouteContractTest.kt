package com.docuhyphen.app.api.resource.exchange

import jakarta.ws.rs.PATCH
import jakarta.ws.rs.PUT
import jakarta.ws.rs.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * The Exchange Fields values have one write surface. A sparse change is a `PATCH` of
 * `/exchanges/{id}/fields`, and no second spelling of the same change is routed beside it.
 */
class ExchangeFieldsResourceRouteContractTest
{
    private fun verbsRoutedTo(path: String): List<String> =
        ExchangeFieldsResource::class.java.declaredMethods
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
