package com.docuhyphen.app.api.service.informationrequest.noauth

import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

internal object InformationRequestNoAuthAccessDoubles {
    fun current(): InformationRequestNoAuthReadAccessService = keepingAccess(mock())

    fun keepingAccess(service: InformationRequestNoAuthReadAccessService): InformationRequestNoAuthReadAccessService {
        whenever(service.requireCurrent(any(), any())).thenAnswer { it.getArgument<RequestAccessContext>(1) }
        whenever(service.confirmCurrent(any(), any())).thenAnswer { it.getArgument<RequestAccessContext>(1) }
        whenever(service.readCurrent<Any?>(any(), any(), any())).thenAnswer {
            it.getArgument<(RequestAccessContext) -> Any?>(2).invoke(it.getArgument(1))
        }
        return service
    }
}
