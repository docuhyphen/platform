import {WorkflowDesignerState, WorkflowStepSpecDraft} from "../../../models/models.tsx";
import {formatTriggerName} from "../workflowUtils.ts";

export const stepsSummaryOf = (steps: WorkflowStepSpecDraft[]): string =>
    steps.length === 0
        ? "No steps configured"
        : `${steps.length} ${steps.length === 1 ? "step" : "steps"} configured`;

export const configurationSummaryOf = (state: WorkflowDesignerState): string =>
    state.name.trim()
        ? `${state.name}${state.triggerEvent ? `, ${formatTriggerName(state.triggerEvent)}` : ""}`
        : "Workflow name and trigger are not configured";
