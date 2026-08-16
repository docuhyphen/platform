import {useEffect, useState} from "react";
import {useAuth} from "../../../context/AuthContext.tsx";
import {PlatformApiError} from "../../../services/platformOrganizationApi.ts";

export type SubscriptionTrialTransitionMode = "END" | "CONVERT";

interface Options
{
    open: boolean;
    ownerKind: "user" | "organization";
    mode: SubscriptionTrialTransitionMode;
    defaultSeatCapacity: number | null;
    onDismiss: () => void;
    onSaved: () => void;
    onEnd: (reason: string) => Promise<unknown>;
    onConvert: (
        billingFrequency: "MONTHLY" | "ANNUAL",
        currentPeriodEnd: string,
        seatCapacity: number | null,
        reason: string,
    ) => Promise<unknown>;
}

const defaultPeriodEnd = (): string =>
{
    const date = new Date(Date.now() + 30 * 24 * 60 * 60 * 1000);
    return new Date(date.getTime() - date.getTimezoneOffset() * 60_000).toISOString().slice(0, 16);
};

export const useSubscriptionTrialTransitionDialog = (options: Options) =>
{
    const {refreshCurrentSession} = useAuth();
    const [billingFrequency, setBillingFrequency] = useState<"MONTHLY" | "ANNUAL">("MONTHLY");
    const [periodEnd, setPeriodEnd] = useState("");
    const [seatCapacity, setSeatCapacity] = useState("");
    const [reason, setReason] = useState("");
    const [saving, setSaving] = useState(false);
    const [error, setError] = useState<string | null>(null);

    useEffect(() =>
    {
        if (!options.open) return;
        setBillingFrequency("MONTHLY");
        setPeriodEnd(defaultPeriodEnd());
        setSeatCapacity(options.defaultSeatCapacity?.toString() ?? "5");
        setReason("");
        setError(null);
    }, [options.defaultSeatCapacity, options.open]);

    const save = async () =>
    {
        const auditedReason = reason.trim();
        if (!auditedReason)
        {
            setError("Reason is required for this audited trial change.");
            return;
        }
        const paidEnd = periodEnd ? new Date(periodEnd) : null;
        const seats = Number(seatCapacity);
        if (options.mode === "CONVERT" && (!paidEnd || paidEnd.getTime() <= Date.now()))
        {
            setError("Paid period end must be in the future.");
            return;
        }
        if (options.mode === "CONVERT" && options.ownerKind === "organization"
            && (!Number.isInteger(seats) || seats <= 0))
        {
            setError("Purchased seats must be a whole number greater than zero.");
            return;
        }

        setSaving(true);
        setError(null);
        try
        {
            if (options.mode === "END")
                await options.onEnd(auditedReason);
            else
                await options.onConvert(
                    billingFrequency,
                    paidEnd!.toISOString(),
                    options.ownerKind === "organization" ? seats : null,
                    auditedReason,
                );
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

    return {
        ownerKind: options.ownerKind,
        mode: options.mode,
        billingFrequency,
        periodEnd,
        seatCapacity,
        reason,
        saving,
        error,
        actionLabel: options.mode === "END" ? "End trial" : "Convert to paid",
        setBillingFrequency,
        setPeriodEnd,
        setSeatCapacity,
        setReason,
        save,
    };
};
