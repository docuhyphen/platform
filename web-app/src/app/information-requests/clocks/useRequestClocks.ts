import {useCallback, useEffect, useRef, useState} from "react";
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
import {informationRequestRefusalMessage, isStaleInformationRequestCommand} from "../shared/informationRequestRefusal.ts";

export interface ClockPolicyChoice
{
    versionId: string;
    label: string;
}

const STALE_CLOCK = "This clock changed while you were working. The latest clocks are shown; try again.";

export const policyChoices = (policies: InformationRequestClockPolicyDto[]): ClockPolicyChoice[] =>
    policies.flatMap(policy =>
    {
        const latest = [...policy.versions].sort((left, right) => right.versionNumber - left.versionNumber)[0];
        return latest ? [{versionId: latest.id, label: `${policy.displayName} (version ${latest.versionNumber})`}] : [];
    });

export const useRequestClocks = (requestId: string) =>
{
    const [clocks, setClocks] = useState<InformationRequestClockDto[]>([]);
    const [policies, setPolicies] = useState<InformationRequestClockPolicyDto[]>([]);
    const [loaded, setLoaded] = useState(false);
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const keys = useRef(new Map<string, string>());

    const load = useCallback(async () =>
    {
        try
        {
            setClocks(await getInformationRequestClocks(requestId));
        }
        catch (caught: unknown)
        {
            setError(informationRequestRefusalMessage(caught, "The clocks could not be loaded."));
        }
        finally
        {
            setLoaded(true);
        }
    }, [requestId]);

    useEffect(() =>
    {
        void load();
        getInformationRequestClockPolicies().then(setPolicies).catch(() => setPolicies([]));
    }, [load]);

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
            setError(isStaleInformationRequestCommand(caught)
                ? STALE_CLOCK
                : informationRequestRefusalMessage(caught, "The clock could not be changed."));
        }
        finally
        {
            setBusy(false);
            await load();
        }
    };

    return {
        clocks,
        choices: policyChoices(policies),
        loaded,
        busy,
        error,
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
