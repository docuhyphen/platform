import {useCallback, useEffect, useState} from "react";
import {
    fetchPlatformSubscriptionTrialRequests,
    SubscriptionTrialRequestApiError,
} from "../../../services/subscriptionTrialRequestApi.ts";
import {
    SubscriptionTrialRequest,
    SubscriptionTrialRequestStatus,
} from "../../../services/types/subscriptionTrialRequests.ts";

export const useTrialRequests = () =>
{
    const pageSize = 25;
    const [status, setStatus] = useState<SubscriptionTrialRequestStatus | "">("PENDING");
    const [offset, setOffset] = useState(0);
    const [items, setItems] = useState<SubscriptionTrialRequest[]>([]);
    const [total, setTotal] = useState(0);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState("");
    const [selected, setSelected] = useState<SubscriptionTrialRequest | null>(null);
    const [decision, setDecision] = useState<"APPROVED" | "REJECTED" | null>(null);

    const reload = useCallback(async () =>
    {
        setLoading(true);
        setError("");
        try
        {
            const result = await fetchPlatformSubscriptionTrialRequests(status, pageSize, offset);
            setItems(result.items);
            setTotal(result.total);
        }
        catch (failure: unknown)
        {
            setError((failure as SubscriptionTrialRequestApiError).errorMessage);
        }
        finally
        {
            setLoading(false);
        }
    }, [offset, status]);

    useEffect(() => { void reload(); }, [reload]);

    const chooseStatus = (value: string) =>
    {
        setStatus(value as SubscriptionTrialRequestStatus | "");
        setOffset(0);
    };

    const openDecision = (request: SubscriptionTrialRequest, nextDecision: "APPROVED" | "REJECTED") =>
    {
        setSelected(request);
        setDecision(nextDecision);
    };

    const closeDecision = () =>
    {
        setSelected(null);
        setDecision(null);
    };

    return {
        pageSize, status, offset, items, total, loading, error, selected, decision,
        setOffset, chooseStatus, openDecision, closeDecision, reload,
    };
};
