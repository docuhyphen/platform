package com.docuhyphen.app.api.service.informationrequest.clock

import com.docuhyphen.app.api.model.entity.InformationRequest
import com.docuhyphen.app.api.model.entity.InformationRequestClockState
import com.docuhyphen.app.api.repository.informationrequest.clock.InformationRequestClockRepository
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.time.Instant

@ApplicationScoped
class InformationRequestClockStopper @Inject constructor(
    private val clockRepository: InformationRequestClockRepository,
    private val recorder: InformationRequestClockRecorder,
) {
    fun stopForFinishedRequest(
        request: InformationRequest,
        actor: PrincipalRef,
        finishedAt: Instant,
        reasonCode: String
    ) {
        clockRepository.findForRequest(request.id)
            .filter { it.state != InformationRequestClockState.STOPPED }
            .sortedBy { it.id }
            .mapNotNull { clockRepository.findForUpdate(it.id) }
            .filter { it.state != InformationRequestClockState.STOPPED }
            .forEach { clock ->
                val stoppedAt = maxOf(finishedAt, clock.startedAt.toInstant())
                recorder.stop(clock, actor, stoppedAt, reasonCode)
            }
    }

    companion object {
        const val REQUEST_FINISHED = "REQUEST_FINISHED"
        const val PARENT_FINISHED = "PARENT_FINISHED"
    }
}
