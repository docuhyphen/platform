package com.docuhyphen.app.api.model.entity

enum class InformationRequestClockType
{
    CALENDAR,
    BUSINESS,
}

enum class InformationRequestClockDueEffect
{
    MARK_OVERDUE,
    EXPIRE_REQUEST,
}

enum class InformationRequestClockUrgency
{
    STANDARD,
    URGENT,
}

enum class InformationRequestClockState
{
    RUNNING,
    PAUSED,
    STOPPED,
}

enum class InformationRequestClockEventKind
{
    STARTED,
    PAUSED,
    RESUMED,
    EXTENDED,
    REMINDED,
    OVERDUE,
    ESCALATED,
    EXPIRED,
    STOPPED,
}
