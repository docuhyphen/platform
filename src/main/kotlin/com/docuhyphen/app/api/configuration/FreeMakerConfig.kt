package com.docuhyphen.app.api.configuration

import freemarker.template.Configuration
import freemarker.template.TemplateExceptionHandler
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
class FreeMarkerConfig {

    val configuration: Configuration = Configuration(Configuration.VERSION_2_3_32).apply {
        setClassLoaderForTemplateLoading(
            Thread.currentThread().contextClassLoader,
            "email-templates"
        )
        defaultEncoding = "UTF-8"
        templateExceptionHandler = TemplateExceptionHandler.RETHROW_HANDLER
        logTemplateExceptions = false
        wrapUncheckedExceptions = true
    }
}