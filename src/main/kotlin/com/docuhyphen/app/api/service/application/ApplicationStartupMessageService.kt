package com.docuhyphen.app.api.service.application

import io.quarkus.runtime.StartupEvent
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.event.Observes
import org.slf4j.LoggerFactory

@ApplicationScoped
class ApplicationStartupMessageService
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(ApplicationStartupMessageService::class.java)
    }

    fun onStart(@Observes event: StartupEvent)
    {
        logger.info("DocuHyphen started and ready")
    }
}
