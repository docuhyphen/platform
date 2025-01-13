package com.securedocsshare.app.service

import com.securedocsshare.app.api.model.AppUser
import com.securedocsshare.app.api.model.MultifactorAuthenticationType
import com.securedocsshare.app.api.model.MfaRecord
import com.securedocsshare.app.repository.MfaRepository
import jakarta.enterprise.context.ApplicationScoped
import jakarta.transaction.Transactional

@ApplicationScoped
class MfaService(
    private val emailService: EmailService,
    private val smsService: SmsService,
    private val passkeyService: PasskeyService,
    private val mfaRepository: MfaRepository
)
{
    @Transactional
    fun doEmailMFA(user: AppUser, mfaRecord: MfaRecord)
    {
        emailService.sendEmail(user.email, "Your MFA Code", "Your code is: ${mfaRecord.mfaToken}")
    }

    @Transactional
    fun doSmsMFA(user: AppUser, mfaRecord: MfaRecord)
    {
        val phoneNumber = user.person?.contactDetails?.phoneNumber
            ?: throw IllegalArgumentException("SMS MFA Failed. Phone number is null")

        smsService.sendSms(phoneNumber, "Your MFA Code: ${mfaRecord.mfaToken}")
    }

    @Transactional
    fun doPasskeyMFA(user: AppUser, mfaRecord: MfaRecord)
    {
        passkeyService.initiatePasskeyAuthentication(user)
    }

    fun getMfaRecordByToken(otp: String, mfaType: MultifactorAuthenticationType): MfaRecord?
    {
        return mfaRepository.findMfaRecordByOtpAndType(otp, mfaType)
    }

    fun getMfaRecordByEmailAndOtp(email: String, otp: String): MfaRecord?
    {
        return mfaRepository.findMfaRecordByEmailAndToken(email, otp)
    }

    fun saveMfaRecord(mfaRecord: MfaRecord): MfaRecord
    {
        return mfaRepository.save(mfaRecord);
    }
}
