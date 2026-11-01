import {useEffect} from "react";
import {AssigneeSpecDraft, EscalationAction, EscalationSpecDraft, WorkflowStepSpecDraft} from "../../../../models/models.tsx";

const DEFAULT_ESCALATION: EscalationSpecDraft = {
    afterSlaBreach: "AUTO_REJECT",
    escalateTo: [],
};

export function useApprovalSlaPatches(step: WorkflowStepSpecDraft, onPatch: (patch: Partial<WorkflowStepSpecDraft>) => void)
{
    const escalationAction: EscalationAction = step.escalation?.afterSlaBreach ?? "AUTO_REJECT";

    useEffect(() =>
    {
        if (step.slaMinutes !== undefined && !step.escalation)
        {
            onPatch({escalation: DEFAULT_ESCALATION});
        }
    }, [onPatch, step.escalation, step.slaMinutes]);

    const patchSlaMinutes = (value: string) =>
    {
        const parsedSlaMinutes = value ? parseInt(value, 10) : undefined;
        const slaMinutes = parsedSlaMinutes !== undefined && Number.isFinite(parsedSlaMinutes)
            ? parsedSlaMinutes
            : undefined;
        onPatch({
            slaMinutes,
            escalation: slaMinutes !== undefined ? step.escalation ?? DEFAULT_ESCALATION : undefined,
        });
    };

    const patchEscalationAction = (afterSlaBreach: EscalationAction) =>
    {
        onPatch({
            escalation: {
                afterSlaBreach,
                escalateTo: step.escalation?.escalateTo ?? [],
            },
        });
    };

    const patchEscalationTargets = (escalateTo: AssigneeSpecDraft[]) =>
    {
        onPatch({
            escalation: {
                afterSlaBreach: escalationAction,
                escalateTo,
            },
        });
    };

    return {escalationAction, patchSlaMinutes, patchEscalationAction, patchEscalationTargets};
}
