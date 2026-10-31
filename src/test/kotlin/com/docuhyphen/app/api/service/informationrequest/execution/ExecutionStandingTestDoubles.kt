package com.docuhyphen.app.api.service.informationrequest.execution

import com.docuhyphen.app.api.model.entity.InformationRequest
import com.docuhyphen.app.api.model.informationrequest.execution.InformationRequestExecutionStanding
import com.docuhyphen.app.api.model.informationrequest.execution.InformationRequestExecutionStandingKind
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

object ExecutionStandingTestDoubles {
    fun active(): InformationRequestExecutionStandingService =
        standing(InformationRequestExecutionStanding(InformationRequestExecutionStandingKind.ACTIVE))

    fun standing(standing: InformationRequestExecutionStanding): InformationRequestExecutionStandingService {
        val service = mock<InformationRequestExecutionStandingService>()
        whenever(service.standingFor(any())).thenReturn(standing)
        whenever(service.standingsOf(any())).thenAnswer { invocation ->
            invocation.getArgument<Collection<InformationRequest>>(0).associate { it.id to standing }
        }
        return service
    }
}
