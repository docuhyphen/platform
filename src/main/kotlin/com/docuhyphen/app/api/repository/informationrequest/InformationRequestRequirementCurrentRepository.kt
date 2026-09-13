package com.docuhyphen.app.api.repository.informationrequest

import com.docuhyphen.app.api.model.entity.InformationRequestRequirementCurrent
import com.docuhyphen.app.api.repository.BaseRepository
import jakarta.enterprise.context.ApplicationScoped
import java.util.UUID

@ApplicationScoped
class InformationRequestRequirementCurrentRepository :
    BaseRepository<InformationRequestRequirementCurrent>(InformationRequestRequirementCurrent::class.java)
{
    fun findForRequirement(requirementId: UUID): InformationRequestRequirementCurrent? =
        findById(requirementId)
}
