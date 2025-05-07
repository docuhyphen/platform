package com.dochyphen.app.api.service.communication

import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject

@ApplicationScoped
class PhoneService @Inject constructor()
{
    fun sendSms(toPhoneNumber: String, subject: String, body: String)
    {
        println("Sending SMS to $toPhoneNumber with subject: $subject and body: $body")
    }

    fun sendWhatsApp(to: String, subject: String, body: String)
    {
        TODO("Not yet implemented")
    }
}
