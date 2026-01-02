package com.docuhyphen.app.api.service.communication

import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
class AppNotificationService
{
    fun sendNotification(appUserId: String, subject: String, body: String)
    {
        println("Sending notification")
    }
}