import {useEffect, useState} from "react";
import {useAuth} from "../../../context/AuthContext.tsx";
import {PlatformApiError} from "../../../services/platformOrganizationApi.ts";

export interface SubscriptionTrialBehaviorOptions
{
    open: boolean;
    ownerKind: "user" | "organization";
    isExtension: boolean;
    currentPeriodEnd: string | null;
    onDismiss: () => void;
    onSaved: () => void;
    onStart: (durationDays: number, seatCapacity: number | null, reason: string) => Promise<unknown>;
    onExtend: (currentPeriodEnd: string, reason: string) => Promise<unknown>;
}

const toDateTimeInput = (value: string | null): string => value ? value.slice(0, 16) : "";

export const useSubscriptionTrialDialog = (options: SubscriptionTrialBehaviorOptions) =>
{
    const {refreshCurrentSession} = useAuth();
    const [durationDays, setDurationDays] = useState(options.ownerKind === "user" ? "14" : "30");
    const [seatCapacity, setSeatCapacity] = useState("5");
    const [extensionEnd, setExtensionEnd] = useState("");
    const [reason, setReason] = useState("");
    const [saving, setSaving] = useState(false);
    const [error, setError] = useState<string | null>(null);

    useEffect(() =>
    {
        if (!options.open) return;
        setDurationDays(options.ownerKind === "user" ? "14" : "30");
        setSeatCapacity("5");
        setExtensionEnd(toDateTimeInput(options.currentPeriodEnd));
        setReason("");
        setError(null);
    }, [options.currentPeriodEnd, options.open, options.ownerKind]);

    const save = async () =>
    {
        const auditedReason = reason.trim();
        const duration = Number(durationDays);
        const seats = Number(seatCapacity);
        if (!auditedReason)
        {
            setError("Reason is required for this audited trial change.");
            return;
        }
        if (!options.isExtension && (!Number.isInteger(duration) || duration <= 0))
        {
            setError("Trial duration must be a whole number greater than zero.");
            return;
        }
        if (!options.isExtension && options.ownerKind === "organization" && (!Number.isInteger(seats) || seats <= 0))
        {
            setError("Trial seats must be a whole number greater than zero.");
            return;
        }
        const nextEnd = extensionEnd ? new Date(extensionEnd) : null;
        if (options.isExtension && (!nextEnd || Number.isNaN(nextEnd.getTime())))
        {
            setError("A valid new trial end is required.");
            return;
        }
        if (options.isExtension && options.currentPeriodEnd
            && nextEnd!.getTime() <= new Date(options.currentPeriodEnd).getTime())
        {
            setError("The new trial end must be after the current trial end.");
            return;
        }

        setSaving(true);
        setError(null);
        try
        {
            if (options.isExtension)
                await options.onExtend(nextEnd!.toISOString(), auditedReason);
            else
                await options.onStart(duration, options.ownerKind === "organization" ? seats : null, auditedReason);
            await refreshCurrentSession();
            options.onSaved();
            options.onDismiss();
        }
        catch (saveError: unknown)
        {
            setError((saveError as PlatformApiError).errorMessage || "Failed to update the trial.");
        }
        finally
        {
            setSaving(false);
        }
    };

    const planName = options.ownerKind === "user" ? "Personal" : "Business";
    return {
        ownerKind: options.ownerKind,
        isExtension: options.isExtension,
        durationDays,
        seatCapacity,
        extensionEnd,
        reason,
        saving,
        error,
        actionLabel: `${options.isExtension ? "Extend" : "Start"} ${planName} trial`,
        setDurationDays,
        setSeatCapacity,
        setExtensionEnd,
        setReason,
        save,
    };
};
