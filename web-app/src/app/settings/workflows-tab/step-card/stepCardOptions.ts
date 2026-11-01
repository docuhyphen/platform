import {WorkflowStepType} from "../../../models/models.tsx";

export const STEP_TYPE_LABELS: Record<WorkflowStepType, string> = {
    APPROVAL: "Approval",
    NOTIFICATION: "Notification",
    CONDITION: "Condition",
    ACTION: "Action",
    WAIT_FOR_COUNTERPARTY_CLEARANCE: "Wait for Counterparty Clearance",
};

export const BUILT_IN_ACTION_KEYS = ["exchange.auto-accept", "exchange.send-reminder", "exchange.revoke-access"];
