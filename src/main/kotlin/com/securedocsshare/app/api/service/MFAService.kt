package com.securedocsshare.app.api.service

import com.securedocsshare.app.api.model.AppUser
import com.securedocsshare.app.api.model.MultifactorAuthenticationType
import com.securedocsshare.app.api.model.MfaRecord
import com.securedocsshare.app.api.repository.MfaRecordRepository
import jakarta.enterprise.context.ApplicationScoped
import jakarta.transaction.Transactional

@ApplicationScoped
class MfaService(
    private val emailService: EmailService,
    private val smsService: SmsService,
    private val passkeyService: PasskeyService,
    private val mfaRecordRepository: MfaRecordRepository
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

    fun getMfaRecordByEmail(email: String): MfaRecord?
    {
        return mfaRecordRepository.findByEmail(email)
    }

    fun getLatestMfaRecordByEmail(email: String): MfaRecord?
    {
        return mfaRecordRepository.findLatestByEmail(email)
    }

    fun getByEmailAndToken(token: String, email: String): MfaRecord?
    {
        return mfaRecordRepository.findByEmailAndToken(token, email)
    }

    fun saveMfaRecord(mfaRecord: MfaRecord): MfaRecord
    {
        return mfaRecordRepository.save(mfaRecord);
    }

    fun saveRecord(mfaRecord: MfaRecord)
    {
        mfaRecordRepository.save(mfaRecord)
    }

    fun updateRecord(record: MfaRecord)
    {
        mfaRecordRepository.update(record)
    }

    fun getMfaRecordByTokenAndType(token: String, type: MultifactorAuthenticationType): MfaRecord?
    {
        return mfaRecordRepository.findByTokenAndType(token, type)
    }

    fun removeMfaRecord(record: MfaRecord)
    {
        return mfaRecordRepository.delete(record)
    }
}
