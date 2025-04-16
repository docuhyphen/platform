package com.dochyphen.app.api.model.entity

import com.dochyphen.app.api.serializer.TimestampSerializer
import com.dochyphen.app.api.serializer.UUIDSerializer
import jakarta.persistence.*
import kotlinx.serialization.Serializable
import java.sql.Timestamp
import java.time.Instant
import java.util.*

enum class ApplicationType {
    WEB,
    MOBILE,

    // Typically represents backend services or microservices that directly
    // use your API as part of their core functionality. These are usually
    // standalone applications that perform specific business functions and
    // continuously interact with your system in a consistent pattern.
    SERVICE,

    // Represents applications that serve as connectors or middleware between our
    // system and other third-party systems. Integration applications focus on
    // data synchronization, format conversion, or acting as an intermediary between
    // different platforms.
    INTEGRATION
}

@Entity
@Table(name = "application")
@Serializable
class Application {
    @Id
    @Serializable(with = UUIDSerializer::class)
    var id: UUID = UUID.randomUUID()

    @Column(name = "created_date", nullable = false)
    @Serializable(with = TimestampSerializer::class)
    var createdDate: Timestamp = Timestamp.from(Instant.now())

    @Column(name = "name", nullable = false)
    lateinit var name: String

    @Column(name = "description")
    var description: String? = null

    @Column(name = "api_key", nullable = false)
    lateinit var apiKey: String

    @Column(name = "api_secret", nullable = false)
    lateinit var apiSecret: String

    @Column(name = "is_active", nullable = false)
    var isActive: Boolean = true

    @Column(name = "rate_limit_per_minute", nullable = false)
    var rateLimitPerMinute: Int = 100

    @Column(name = "ip_whitelist")
    var ipWhitelist: String? = null

    @Enumerated(EnumType.STRING)
    @Column(name = "application_type", nullable = false)
    var applicationType: ApplicationType = ApplicationType.SERVICE

    @Column(name = "last_access_date")
    @Serializable(with = TimestampSerializer::class)
    var lastAccessDate: Timestamp? = null

    constructor()
}