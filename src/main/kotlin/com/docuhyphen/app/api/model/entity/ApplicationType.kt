package com.docuhyphen.app.api.model.entity

enum class ApplicationType
{
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