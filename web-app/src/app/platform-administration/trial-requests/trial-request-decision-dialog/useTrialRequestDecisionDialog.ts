import {useEffect, useState} from "react";
import {
    decidePlatformSubscriptionTrialRequest,
    SubscriptionTrialRequestApiError,
} from "../../../../services/subscriptionTrialRequestApi.ts";
import {SubscriptionTrialRequest} from "../../../../services/types/subscriptionTrialRequests.ts";

interface Options
{
    request: SubscriptionTrialRequest | null;
    decision: "APPROVED" | "REJECTED" | null;
    onSaved: () => void;
}

export const useTrialRequestDecisionDialog = ({request, decision, onSaved}: Options) =>
{
    const [durationDays, setDurationDays] = useState("14");
    const [seatCapacity, setSeatCapacity] = useState("5");
    const [reason, setReason] = useState("");
    const [saving, setSaving] = useState(false);
    const [error, setError] = useState("");
    const open = request !== null && decision !== null;
    const approving = decision === "APPROVED";

    useEffect(() =>
    {
        if (!open) return;
        setDurationDays(request?.ownerType === "ORGANIZATION" ? "30" : "14");
        setSeatCapacity("5");
        setReason("");
        setError("");
    }, [open, request?.id, request?.ownerType]);

    const save = async () =>
    {
        if (!request || !decision) return;
        if (!reason.trim()) return setError("Decision reason is required.");
        if (approving && (!Number.isInteger(Number(durationDays)) || Number(durationDays) <= 0))
        {
            return setError("Duration must be a positive whole number of days.");
        }
        if (approving && request.ownerType === "ORGANIZATION"
            && (!Number.isInteger(Number(seatCapacity)) || Number(seatCapacity) <= 0))
        {
            return setError("Trial seats must be a positive whole number.");
        }
        setSaving(true);
        setError("");
        try
        {
            await decidePlatformSubscriptionTrialRequest(request.id, {
                status: decision,
                reason: reason.trim(),
                ...(approving ? {durationDays: Number(durationDays)} : {}),
                ...(approving && request.ownerType === "ORGANIZATION"
                    ? {seatCapacity: Number(seatCapacity)} : {}),
            });
            onSaved();
        }
        catch (failure: unknown)
        {
            setError((failure as SubscriptionTrialRequestApiError).errorMessage);
        }
        finally
        {
            setSaving(false);
        }
    };

    return {
        open, approving, durationDays, seatCapacity, reason, saving, error,
        setDurationDays, setSeatCapacity, setReason, save,
    };
};
