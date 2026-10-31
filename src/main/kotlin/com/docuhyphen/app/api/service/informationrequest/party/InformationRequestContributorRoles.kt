package com.docuhyphen.app.api.service.informationrequest.party

import com.docuhyphen.app.api.model.entity.InformationRequestContributorRole
import com.docuhyphen.app.api.model.entity.InformationRequestShareRoleKey

fun InformationRequestContributorRole.shareRoleKey(): InformationRequestShareRoleKey =
    when (this) {
        InformationRequestContributorRole.SUBJECT -> InformationRequestShareRoleKey.SUBJECT
        InformationRequestContributorRole.CONTRIBUTOR -> InformationRequestShareRoleKey.CONTRIBUTOR
        InformationRequestContributorRole.PREPARER -> InformationRequestShareRoleKey.PREPARER
        InformationRequestContributorRole.ATTESTOR -> InformationRequestShareRoleKey.ATTESTOR
    }
