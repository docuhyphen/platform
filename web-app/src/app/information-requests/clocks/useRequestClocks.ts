import {useRef, useState} from "react";
import {
    changeInformationRequestClock,
    getInformationRequestClockPolicies,
    InformationRequestClockChangePath,
    startInformationRequestClock,
} from "../../../services/informationRequestAdministrationService.ts";
import {getInformationRequestClocks} from "../../../services/informationRequestOperationsService.ts";
import {
    InformationRequestClockDto,
    InformationRequestClockPolicyDto,
    InformationRequestClockUrgency,
} from "../../models/models.tsx";
import {
    informationRequestRefusalMessage,
    isStaleInformationRequestCommand
} from "../shared/informationRequestRefusal.ts";
import {InformationRequestOwnerFields, useOwnerWorkspace, workspaceScopeOf} from "../shared/ownerWorkspace.ts";
import {Scoped, valueInScope} from "../shared/useScopeGuard.ts";
import {useServerRead} from "../shared/useServerRead.ts";

export interface ClockPolicyChoice
{
    versionId: string;
    label: string;
}

const STALE_CLOCK = "This clock changed while you were working. The latest clocks are shown; try again.";

const clockRefusal = (refusal: unknown) => informationRequestRefusalMessage(refusal, "The clocks could not be loaded.");

const policyRefusal = (refusal: unknown) =>
    informationRequestRefusalMessage(refusal, "The due date policies could not be loaded.");

export const policyChoices = (policies: InformationRequestClockPolicyDto[]): ClockPolicyChoice[] =>
    policies.flatMap(policy =>
    {
        const latest = [...policy.versions].sort((left, right) => right.versionNumber - left.versionNumber)[0];
        return latest ? [{versionId: latest.id, label: `${policy.displayName} (version ${latest.versionNumber})`}] : [];
    });

export const useRequestClocks = (requestId: string, owner: InformationRequestOwnerFields) =>
{
    const workspace = useOwnerWorkspace(owner);
    const ownerWorkspace = workspace.kind === "current";
    const clocks = useServerRead(requestId, () => getInformationRequestClocks(requestId), clockRefusal);
    const policies = useServerRead(
        `${requestId}|${workspaceScopeOf(workspace)}`,
        () => ownerWorkspace ? getInformationRequestClockPolicies() : Promise.resolve([]),
        policyRefusal,
    );
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState<Scoped<string> | null>(null);
    const keys = useRef(new Map<string, string>());

    const run = async (signature: string, command: (key: string) => Promise<InformationRequestClockDto>) =>
    {
        const key = keys.current.get(signature) ?? crypto.randomUUID();
        keys.current.set(signature, key);
        setBusy(true);
        setError(null);
        try
        {
            await command(key);
            keys.current.delete(signature);
        }
        catch (caught: unknown)
        {
            if (isStaleInformationRequestCommand(caught)) keys.current.delete(signature);
            setError({
                scope: requestId,
                value: isStaleInformationRequestCommand(caught)
                    ? STALE_CLOCK
                    : informationRequestRefusalMessage(caught, "The clock could not be changed."),
            });
        }
        finally
        {
            setBusy(false);
            await clocks.reload();
        }
    };

    return {
        clocks: clocks.value,
        choices: ownerWorkspace && policies.value ? policyChoices(policies.value) : null,
        policiesInOtherWorkspace: workspace.kind === "other",
        loadError: clocks.error ?? (ownerWorkspace ? policies.error : null),
        reload: async () => {
            await Promise.all([clocks.reload(), policies.reload()]);
        },
        busy,
        error: valueInScope(error, requestId),
        start: (policyVersionId: string, urgency: InformationRequestClockUrgency, clockKey: string) =>
            run(`start:${clockKey}:${policyVersionId}:${urgency}`, key =>
                startInformationRequestClock(requestId, {clockKey, policyVersionId, urgency}, key)),
        change: (clock: InformationRequestClockDto, path: InformationRequestClockChangePath, reasonCode: string, extensionMinutes?: number) =>
            run(`${path}:${clock.id}:${clock.clockETag}:${reasonCode}:${extensionMinutes ?? ""}`, key =>
                changeInformationRequestClock(
                    requestId,
                    clock.id,
                    path,
                    extensionMinutes === undefined ? {reasonCode} : {reasonCode, extensionMinutes},
                    clock.clockETag,
                    key,
                )),
    };
};
