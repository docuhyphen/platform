package com.docuhyphen.app.api.service.informationrequest.submission

import com.docuhyphen.app.api.model.entity.InformationRequestShareRoleKey
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

internal object InformationRequestRespondentWorkDoubles {
    fun everyRespondingRoleOutstanding(): InformationRequestRespondentWork =
        mock<InformationRequestRespondentWork>().also {
            whenever(it.rolesWithOutstandingItems(any())).thenReturn(InformationRequestShareRoleKey.RESPONDING_ROLES)
        }
}
