package com.docuhyphen.app.api.model.informationrequest.access

data class InformationRequestAuthorActions(
    val issue: Boolean = false,
    val cancel: Boolean = false,
    val supersede: Boolean = false,
    val changeCompletionGate: Boolean = false,
    val manageParties: Boolean = false,
    val previewRecipients: Boolean = false,
    val manageClocks: Boolean = false,
    val scheduleFollowUps: Boolean = false,
    val createFollowUps: Boolean = false,
    val promoteFacts: Boolean = false,
    val revokeFacts: Boolean = false,
    val recordDecisions: Boolean = false,
    val issueAccessLinks: Boolean = false,
)
