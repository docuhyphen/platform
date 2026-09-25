package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.model.entity.InformationRequestRequirement
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateBindingEvidenceLink
import com.docuhyphen.app.api.model.informationrequest.InformationRequestSupportingEvidenceLinkTarget

object InformationRequestSupportingEvidenceLinkResolver
{
    private const val PATH_SEPARATOR = "/"

    fun resolve(
        templateLinks: List<InformationRequestTemplateBindingEvidenceLink>,
        requirements: List<InformationRequestRequirement>,
    ): List<InformationRequestSupportingEvidenceLinkTarget>
    {
        val requirementsByBinding = requirements.groupBy { it.sourceTemplateBindingId }
        return templateLinks.flatMap { link ->
            val candidates = requirementsByBinding[link.supportingTemplateBindingId].orEmpty()
            requirementsByBinding[link.templateBindingId].orEmpty().flatMap { supported ->
                supportingOccurrences(supported.occurrencePath, candidates).map { supporting ->
                    InformationRequestSupportingEvidenceLinkTarget(link.id, supported.id, supporting.id)
                }
            }
        }
    }

    private fun supportingOccurrences(
        occurrencePath: String,
        candidates: List<InformationRequestRequirement>,
    ): List<InformationRequestRequirement>
    {
        ancestry(occurrencePath).forEach { level ->
            val atLevel = candidates.filter { it.occurrencePath == level }
            if (atLevel.isNotEmpty()) return atLevel
        }
        return candidates.filter { isDescendant(it.occurrencePath, occurrencePath) }
    }

    private fun ancestry(occurrencePath: String): List<String>
    {
        if (InformationRequestOccurrencePath.isRoot(occurrencePath)) return listOf(InformationRequestOccurrencePath.ROOT)
        val segments = occurrencePath.split(PATH_SEPARATOR)
        return segments.indices.reversed().map { depth -> segments.subList(0, depth + 1).joinToString(PATH_SEPARATOR) } +
            InformationRequestOccurrencePath.ROOT
    }

    private fun isDescendant(candidatePath: String, occurrencePath: String): Boolean =
        if (InformationRequestOccurrencePath.isRoot(occurrencePath)) !InformationRequestOccurrencePath.isRoot(candidatePath)
        else candidatePath.startsWith(occurrencePath + PATH_SEPARATOR)
}
