package com.docuhyphen.app.api.service.communication.templates

import com.docuhyphen.app.api.configuration.FreeMarkerConfig
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.io.StringWriter

@ApplicationScoped
class EmailTemplateRenderer @Inject constructor(
    private val freeMarkerConfig: FreeMarkerConfig,
)
{
    fun render(templateName: String, model: Map<String, Any>): String
    {
        val template = freeMarkerConfig.configuration.getTemplate(templateName)
        val writer = StringWriter()
        template.process(model, writer)
        return writer.toString()
    }
}


