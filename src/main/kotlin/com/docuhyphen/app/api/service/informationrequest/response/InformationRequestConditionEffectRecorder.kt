package com.docuhyphen.app.api.service.informationrequest.response

import com.docuhyphen.app.api.model.entity.InformationRequestResponseConditionEffect
import com.docuhyphen.app.api.model.entity.InformationRequestTransition
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.response.InformationRequestConditionEffectDescriptor
import com.docuhyphen.app.api.repository.informationrequest.response.InformationRequestResponseConditionEffectRepository
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.put
import java.sql.Timestamp

@ApplicationScoped
class InformationRequestConditionEffectRecorder @Inject constructor(
    private val effectRepository: InformationRequestResponseConditionEffectRepository,
) {
    fun auditDetails(effects: List<InformationRequestConditionEffectDescriptor>): Map<String, String> {
        if (effects.isEmpty()) return emptyMap()
        return mapOf(
            CONDITION_EFFECT_COUNT to effects.size.toString(),
            CONDITION_EFFECTS to buildJsonArray {
                effects.forEach { effect ->
                    addJsonObject {
                        put("requirementId", effect.requirement.id.toString())
                        put("occurrencePath", effect.requirement.occurrencePath)
                        put("ruleKey", effect.ruleKey)
                        put("expressionVersion", effect.expressionVersion)
                        put("evaluationState", effect.evaluationState.name)
                        put("hiddenDataPolicy", effect.hiddenDataPolicy.name)
                        put("effect", effect.effect.name)
                        effect.archiveId?.let { put("archiveId", it.toString()) }
                    }
                }
            }.toString(),
        )
    }

    fun record(
        transition: InformationRequestTransition,
        effects: List<InformationRequestConditionEffectDescriptor>,
        access: RequestAccessContext,
        now: Timestamp,
    ) {
        effects.forEach { effect ->
            effectRepository.save(
                InformationRequestResponseConditionEffect().apply {
                    informationRequestId = transition.informationRequestId
                    informationRequestRequirementId = effect.requirement.id
                    responseId = effect.response.id
                    transitionId = transition.id
                    occurrencePath = effect.requirement.occurrencePath
                    ruleKey = effect.ruleKey
                    expressionVersion = effect.expressionVersion
                    evaluationState = effect.evaluationState
                    hiddenDataPolicy = effect.hiddenDataPolicy
                    this.effect = effect.effect
                    archiveId = effect.archiveId
                    responseRevision = effect.response.responseRevision
                    recordedByPrincipalKind = access.principal.kind
                    recordedByPrincipalId = access.principal.id
                    recordedBySessionRef = access.authorization.sessionRef
                    occurredAt = now
                },
            )
        }
    }

    companion object {
        const val CONDITION_EFFECT_COUNT = "conditionEffectCount"
        const val CONDITION_EFFECTS = "conditionEffects"
    }
}
