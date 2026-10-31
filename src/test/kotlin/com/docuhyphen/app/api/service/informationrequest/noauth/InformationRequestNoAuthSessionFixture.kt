package com.docuhyphen.app.api.service.informationrequest.noauth

import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestParentSnapshot
import com.docuhyphen.app.api.model.informationrequest.noauth.IssuedRequestAccessSession
import com.docuhyphen.app.api.repository.informationrequest.noauth.RequestAccessSessionRepository
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.informationrequest.access.InformationRequestAccessContextFactory
import com.docuhyphen.app.api.service.informationrequest.access.RequestAccessSessionStepUpWindow
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.sql.Timestamp
import java.time.Duration
import java.time.Instant
import java.util.*

internal class InformationRequestNoAuthSessionFixture {
    val requestId: UUID = UUID.randomUUID()
    val participant: PrincipalRef = PrincipalRef.participant(UUID.randomUUID())
    val link = ShareLink().apply {
        shareId = UUID.randomUUID()
        tokenHash = "bootstrap-hash"
        linkMode = ShareLinkMode.VERIFICATION_BOOTSTRAP
        expiresAt = Timestamp.from(Instant.now().plusSeconds(3600))
    }
    val party = InformationRequestParty().apply {
        informationRequestId = requestId
        roleKey = InformationRequestShareRoleKey.CONTRIBUTOR
        principalKind = participant.kind
        principalId = participant.id
        shareId = link.shareId
        active = true
    }
    val repository: RequestAccessSessionRepository = mock()
    val proof: InformationRequestContactProofService = mock()
    val sessions = RequestAccessSessionService(repository)
    val service = InformationRequestNoAuthReadAccessService(
        proof,
        sessions,
        InformationRequestAccessContextFactory(mock(), RequestAccessSessionStepUpWindow(Duration.ofMinutes(15))),
    )
    private val saved = mutableMapOf<UUID, RequestAccessSession>()

    init {
        whenever(repository.requestStateForShare(any()))
            .thenReturn(com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestState.ISSUED)
        whenever(repository.lockParentForShare(any()))
            .thenReturn(InformationRequestParentSnapshot(ExchangeStatus.ACCEPTED_STARTED, lockedForUpdate = true))
        whenever(repository.save(any())).thenAnswer {
            it.getArgument<RequestAccessSession>(0).also { row -> saved[row.id] = row }
        }
        whenever(repository.update(any())).thenAnswer { it.getArgument<RequestAccessSession>(0) }
        whenever(repository.findSessionByIdForUpdate(any())).thenAnswer { saved[it.getArgument(0)] }
        whenever(repository.findById(any())).thenAnswer { saved[it.getArgument(0)] }
        whenever(proof.resolveBootstrapLink(BOOTSTRAP_TOKEN)).thenReturn(link to party)
        whenever(proof.resolveBootstrapLinkById(link.id)).thenReturn(link to party)
    }

    fun issue(): IssuedRequestAccessSession =
        sessions.issue(link, participant, RequestAccessSessionVerificationStrength.EMAIL_OTP, null)

    companion object {
        const val BOOTSTRAP_TOKEN = "bootstrap"
    }
}
