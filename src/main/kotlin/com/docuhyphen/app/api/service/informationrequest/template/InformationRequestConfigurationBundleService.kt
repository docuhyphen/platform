package com.docuhyphen.app.api.service.informationrequest.template

import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.template.InformationRequestConfigurationBundleParse
import com.docuhyphen.app.api.model.informationrequest.template.InformationRequestConfigurationBundleProblem
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject

@ApplicationScoped
class InformationRequestConfigurationBundleService @Inject constructor(
    private val validator: InformationRequestConfigurationBundleValidator,
    private val authoringService: InformationRequestTemplateAuthoringService,
) {
    fun check(document: String, access: RequestAccessContext): List<InformationRequestConfigurationBundleProblem> =
        when (val parsed = validator.parse(document)) {
            is InformationRequestConfigurationBundleParse.Parsed -> validator.validate(
                parsed.bundle,
                authoringService.frozenVersionContents(
                    access,
                    parsed.bundle.templateVersions.map { it.templateKey }.toSet()
                ),
            )

            is InformationRequestConfigurationBundleParse.Refused -> parsed.problems
        }
}
