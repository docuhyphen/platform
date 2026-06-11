package com.docuhyphen.app.api.service.communication

import com.docuhyphen.app.api.exception.TooManyRequestsException
import com.docuhyphen.app.api.model.dto.MfaSessionDto
import com.docuhyphen.app.api.model.entity.AppUser
import com.docuhyphen.app.api.model.entity.MfaRecord
import com.docuhyphen.app.api.model.entity.MultifactorAuthenticationStatus.PENDING
import com.docuhyphen.app.api.model.entity.MultifactorAuthenticationType
import com.docuhyphen.app.api.repository.MfaRecordRepository
import com.docuhyphen.app.api.service.auth.PasskeyService
import com.docuhyphen.app.api.service.config.ConfigurationService
import jakarta.enterprise.context.ApplicationScoped
import jakarta.transaction.Transactional
import java.sql.Timestamp
import java.time.Instant
import java.util.*
import java.util.concurrent.TimeUnit.MINUTES

@ApplicationScoped
class MfaService(
    private val emailService: EmailService,
    private val smsService: PhoneService,
    private val passkeyService: PasskeyService,
    private val mfaRecordRepository: MfaRecordRepository,
    private val configurationService: ConfigurationService,
    private val otpService: OtpService,
    private val emailTemplateService: EmailTemplateService,
)
{
    @Transactional
    fun createMfaSession(user: AppUser, mfaType: MultifactorAuthenticationType, ipAddress: String): MfaSessionDto
    {
        enforceRateLimits(user.email, ipAddress)

        val sessionId = UUID.randomUUID().toString()

        // Generate and save OTP
        val otp = otpService.generateEmailOtp()
        val hashedOtp = otpService.hashOtp(otp)
        val expirationTime = Timestamp.from(
            Instant.now().plusMillis(MINUTES.toMillis(configurationService.getSignInEmailOtpMFAExpiryMins()))
        )

        val mfaRecord = MfaRecord().apply {
            this.appUser = user
            this.mfaToken = hashedOtp
            this.expiryDateTime = expirationTime
            this.mfaType = mfaType
            this.status = PENDING
            this.sessionId = sessionId
            this.ipAddress = ipAddress
        }

        mfaRecordRepository.save(mfaRecord)
        mfaRecordRepository.deletePendingSessionsByEmailExcept(user.email, sessionId)

        return MfaSessionDto().apply {
            this.id = UUID.fromString(sessionId)
            this.mfaToken = otp
            this.mfaTokenHashed = hashedOtp
        }
    }

    @Transactional
    fun regenerateOtp(mfaRecord: MfaRecord): String {
        // Generate new OTP
        val newOtp = otpService.generateEmailOtp()
        val hashedOtp = otpService.hashOtp(newOtp)
        val now = Timestamp.from(Instant.now())

        // Update record
        mfaRecord.mfaToken = hashedOtp
        // Optionally reset attempts count
        mfaRecord.attemptCount = 0
        mfaRecord.createdDate = now
        // Update expiry time to give full time again
        mfaRecord.expiryDateTime = Timestamp.from(
            Instant.now().plusMillis(MINUTES.toMillis(configurationService.getSignInEmailOtpMFAExpiryMins()))
        )

        updateRecord(mfaRecord)

        return newOtp
    }

    fun enforceRateLimits(email: String, ipAddress: String) {
        val threshold = Timestamp.from(Instant.now().minusSeconds(60))
        val requestCount = mfaRecordRepository.countRecentRequestsByEmailAndIp(email, ipAddress, threshold)

        if (requestCount >= configurationService.getMaxOtpRequestsPerMinute())
        {
            throw TooManyRequestsException("Too many sign in attempts. Please try again shortly.")
        }
    }

    fun doEmailMFA(appUser: AppUser, mfaToken: String)
    {
        val body = emailTemplateService.renderSignInMfaEmail(
            otp = mfaToken,
            expiryMinutes = configurationService.getSignInEmailOtpMFAExpiryMins(),
        )

        emailService.sendEmail(
            to = appUser.email,
            subject = "${configurationService.emailSubjectTitle} | Sign In",
            body = body,
            useHtml = true
        )
    }

    @Transactional
    fun doSmsMFA(user: AppUser, mfaRecord: MfaRecord)
    {
        val phoneNumber = user.person?.contactDetails?.phoneNumber
            ?: throw IllegalArgumentException("SMS MFA Failed. Phone number is null")

        smsService.sendSms(phoneNumber, "TODO", "Your MFA Code: ${mfaRecord.mfaToken}")
    }

    @Transactional
    fun doPasskeyMFA(user: AppUser, mfaRecord: MfaRecord)
    {
        passkeyService.initiatePasskeyAuthentication(user)
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
        return mfaRecordRepository.save(mfaRecord)
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

    fun getMfaRecordByEmailAndSessionId(email: String, sessionId: String): MfaRecord?
    {
        return mfaRecordRepository.findByEmailAndSessionId(email, sessionId)
    }
}