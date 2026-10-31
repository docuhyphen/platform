package com.docuhyphen.app.api.model

import com.docuhyphen.app.api.model.entity.DocumentVersion
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateConditionPredicate
import com.docuhyphen.app.api.model.entity.Share
import jakarta.persistence.Column
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class InformationRequestStorageModelTest {
    @Test
    fun `persisted predicate vocabulary belongs to the model layer`() {
        assertTrue(
            InformationRequestTemplateConditionPredicate::class.java.getDeclaredField("operator").type.packageName
                .startsWith("com.docuhyphen.app.api.model.")
        )
    }

    @Test
    fun `Share resource types retain the storage width needed by long resource names`() {
        assertEquals(64, Share::class.java.getDeclaredField("resourceType").getAnnotation(Column::class.java).length)
    }

    @Test
    fun `a document version can represent an absent legacy parent reference`() {
        assertNull(DocumentVersion().document)
    }
}
