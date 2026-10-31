package com.docuhyphen.app.api.exception

import java.util.*

class WorkflowStartRefusedException(
    val definitionId: UUID,
    val reasonCode: String,
) : RuntimeException("Workflow definition $definitionId cannot start: $reasonCode") {
    companion object {
        const val SPEC_UNREADABLE = "SPEC_UNREADABLE"
        const val ASSIGNEE_INVALID = "ASSIGNEE_INVALID"
        const val OWNER_UNRESOLVED = "OWNER_UNRESOLVED"
    }
}
