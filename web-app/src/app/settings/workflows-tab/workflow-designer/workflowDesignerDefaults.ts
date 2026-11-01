import {WorkflowDesignerState, WorkflowStepSpecDraft} from "../../../models/models.tsx";

export type WorkflowScope = 'PERSONAL' | 'ORG' | 'APP';
export type FormPage = "overview" | "configuration" | "applicability" | "steps";
export type FormPageTransitionDirection = "forward" | "back" | null;

export const defaultStep = (): WorkflowStepSpecDraft => ({
    type: "APPROVAL",
    assignees: [],
    quorum: {kind: "ANY"},
    addons: [],
});

export const defaultState = (): WorkflowDesignerState => ({
    name: "", summary: "", generalTags: [], triggerEvent: "", isActive: false, steps: [],
});
