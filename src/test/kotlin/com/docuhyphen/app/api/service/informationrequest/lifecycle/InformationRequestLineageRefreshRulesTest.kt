package com.docuhyphen.app.api.service.informationrequest.lifecycle

import com.docuhyphen.app.api.model.InformationRequestLineageDtoMapper
import com.docuhyphen.app.api.model.dto.InformationRequestRefreshRuleDto
import com.docuhyphen.app.api.model.entity.InformationRequest
import com.docuhyphen.app.api.model.entity.InformationRequestRefreshRule
import com.docuhyphen.app.api.model.entity.PrincipalKind
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestRefreshSource
import com.docuhyphen.app.api.repository.informationrequest.lifecycle.InformationRequestLineageRepository
import com.docuhyphen.app.api.repository.informationrequest.lifecycle.InformationRequestRecurrenceRepository
import com.docuhyphen.app.api.repository.informationrequest.lifecycle.InformationRequestRefreshRuleRepository
import com.docuhyphen.app.api.service.auth.authz.Action
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.informationrequest.InformationRequestMutationGate
import com.docuhyphen.app.api.service.informationrequest.InformationRequestQueryService
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.sql.Timestamp
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.*

class InformationRequestLineageRefreshRulesTest {
    private val requestId = UUID.randomUUID()
    private val access = RequestAccessContext(PrincipalRef.user(UUID.randomUUID()), AuthorizationContext())

    @Test
    fun `the lineage of a request lists the refresh rules defined for it to someone who manages its follow-ups`() {
        val request = InformationRequest().apply { id = requestId }
        val rule = InformationRequestRefreshRule().apply {
            informationRequestId = requestId
            requirementKey = "supporting-record"
            leadDays = 30
            createdByPrincipalKind = PrincipalKind.USER
            createdByPrincipalId = UUID.randomUUID()
            createdAt = Timestamp.from(Instant.parse("2026-09-12T08:00:00Z"))
        }
        val queryService = mock<InformationRequestQueryService>()
        whenever(queryService.findById(requestId, access)).thenReturn(request)
        val lineageRepository = mock<InformationRequestLineageRepository>()
        whenever(lineageRepository.findForSource(requestId)).thenReturn(emptyList())
        val recurrenceRepository = mock<InformationRequestRecurrenceRepository>()
        val refreshRuleRepository = mock<InformationRequestRefreshRuleRepository>()
        whenever(refreshRuleRepository.findForRequest(requestId)).thenReturn(listOf(rule))
        val gate = mock<InformationRequestMutationGate>()
        whenever(gate.permitsRequest(access, Action.INFORMATION_REQUEST_SUPERSEDE, requestId)).thenReturn(true)
        val service = InformationRequestLineageQueryService(
            queryService,
            lineageRepository,
            recurrenceRepository,
            mock(),
            mock(),
            mock(),
            gate,
            refreshRuleRepository,
            mock(),
            mock(),
            Clock.systemUTC(),
        )

        val view = service.lineage(requestId, access)

        assertEquals(listOf(rule), view.refreshRules.map { it.rule })
        assertEquals(
            listOf(
                InformationRequestRefreshRuleDto(
                    id = rule.id,
                    informationRequestId = requestId,
                    requirementKey = "supporting-record",
                    leadDays = 30,
                    createdAt = rule.createdAt,
                ),
            ),
            InformationRequestLineageDtoMapper.toDto(view).refreshRules,
        )
    }

    @Test
    fun `each refresh rule states when its refresh falls due, whether it is due now and whether it was already made`() {
        val today = LocalDate.of(2026, 10, 29)
        val request = InformationRequest().apply { id = requestId }
        fun ruleFor(key: String) = InformationRequestRefreshRule().apply {
            informationRequestId = requestId
            requirementKey = key
            leadDays = 30
            createdByPrincipalKind = PrincipalKind.USER
            createdByPrincipalId = UUID.randomUUID()
            createdAt = Timestamp.from(Instant.parse("2026-09-12T08:00:00Z"))
        }

        val early = ruleFor("early-record")
        val due = ruleFor("due-record")
        val refreshed = ruleFor("refreshed-record")
        val unsourced = ruleFor("unsourced-record")
        val refreshedPackage = UUID.randomUUID()
        val queryService = mock<InformationRequestQueryService>()
        whenever(queryService.findById(requestId, access)).thenReturn(request)
        val lineageRepository = mock<InformationRequestLineageRepository>()
        whenever(lineageRepository.findForSource(requestId)).thenReturn(emptyList())
        whenever(lineageRepository.existsRefresh(refreshed.id, refreshedPackage)).thenReturn(true)
        val refreshRuleRepository = mock<InformationRequestRefreshRuleRepository>()
        whenever(refreshRuleRepository.findForRequest(requestId)).thenReturn(listOf(early, due, refreshed, unsourced))
        whenever(refreshRuleRepository.findRefreshSource(requestId, "early-record"))
            .thenReturn(InformationRequestRefreshSource(UUID.randomUUID(), today.plusDays(90)))
        whenever(refreshRuleRepository.findRefreshSource(requestId, "due-record"))
            .thenReturn(InformationRequestRefreshSource(UUID.randomUUID(), today.plusDays(10)))
        whenever(refreshRuleRepository.findRefreshSource(requestId, "refreshed-record"))
            .thenReturn(InformationRequestRefreshSource(refreshedPackage, today.plusDays(10)))
        val gate = mock<InformationRequestMutationGate>()
        whenever(gate.permitsRequest(access, Action.INFORMATION_REQUEST_SUPERSEDE, requestId)).thenReturn(true)
        val service = InformationRequestLineageQueryService(
            queryService,
            lineageRepository,
            mock(),
            mock(),
            mock(),
            mock(),
            gate,
            refreshRuleRepository,
            mock(),
            mock(),
            Clock.fixed(today.atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC),
        )

        val rules =
            Json.encodeToJsonElement(InformationRequestLineageDtoMapper.toDto(service.lineage(requestId, access)))
                .jsonObject.getValue("refreshRules").jsonArray.map { it.jsonObject }

        assertEquals(
            listOf("2026-12-28", "2026-10-09", "2026-10-09", null),
            rules.map { it["refreshDueOn"]?.jsonPrimitive?.contentOrNull })
        assertEquals(
            listOf(false, true, false, false),
            rules.map { it["refreshDue"]?.jsonPrimitive?.booleanOrNull ?: false })
        assertEquals(
            listOf(false, false, true, false),
            rules.map { it["refreshed"]?.jsonPrimitive?.booleanOrNull ?: false })
    }
}
