package com.docuhyphen.app.api.service.audit

import com.docuhyphen.app.api.service.audit.catalog.AuditCategory
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import org.eclipse.microprofile.config.inject.ConfigProperty
import java.util.*

@ApplicationScoped
class AuditFailurePolicyResolver @Inject constructor(
    @ConfigProperty(name = "app.audit.failure-policy.fail-closed-categories")
    private val failClosedCategoriesConfig: Optional<String>,
)
{
    private val failClosedCategories: Set<AuditCategory> by lazy {
        failClosedCategoriesConfig
            .orElse("")
            .split(",")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .mapNotNull { name -> runCatching { AuditCategory.valueOf(name.uppercase()) }.getOrNull() }
            .toSet()
    }

    fun resolve(category: AuditCategory): AuditFailurePolicy =
        if (category in failClosedCategories) AuditFailurePolicy.FAIL_CLOSED else AuditFailurePolicy.DEGRADED
}
