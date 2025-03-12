package com.dochyphen.app.api.service.communication

import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
class SmsService
{
    fun sendSms(phoneNumber: String, message: String)
    {
        // Implement SMS sending logic here
        println("Sending SMS to $phoneNumber with message: $message")
    }
}
