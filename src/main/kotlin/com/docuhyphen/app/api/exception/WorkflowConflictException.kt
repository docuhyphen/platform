package com.docuhyphen.app.api.exception

class WorkflowConflictException(message: String, val reasonCode: String? = null) : RuntimeException(message) {
    companion object {
        const val EXCHANGE_ENDING_HELD_BY_WORKFLOW = "EXCHANGE_ENDING_HELD_BY_WORKFLOW"
    }
}
