import {useCallback, useEffect, useState} from "react";
import {fetchPlatformUserSubscriptions, updatePlatformUserSubscription} from "../../../services/platformUserSubscriptionApi.ts";
import {PlatformApiError} from "../../../services/platformOrganizationApi.ts";
import {PlatformUserSubscriptionPolicy, PlatformUserSubscriptionPolicyRequest} from "../../../services/types/platformUserSubscriptions.ts";

const PAGE_SIZE = 25;

export const useUserSubscriptions = () =>
{
    const [items, setItems] = useState<PlatformUserSubscriptionPolicy[]>([]);
    const [query, setQuery] = useState("");
    const [offset, setOffset] = useState(0);
    const [total, setTotal] = useState(0);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);

    const load = useCallback(async () =>
    {
        setLoading(true);
        setError(null);
        try
        {
            const result = await fetchPlatformUserSubscriptions(query.trim(), PAGE_SIZE, offset);
            setItems(result.items);
            setTotal(result.total);
        }
        catch (loadError: unknown)
        {
            setError((loadError as PlatformApiError).errorMessage || "Failed to load user subscriptions.");
        }
        finally
        {
            setLoading(false);
        }
    }, [offset, query]);

    useEffect(() => void load(), [load]);

    const save = async (user: PlatformUserSubscriptionPolicy, request: PlatformUserSubscriptionPolicyRequest) =>
    {
        await updatePlatformUserSubscription(user.appUserId, request);
        await load();
    };

    return {
        items, query, offset, total, loading, error,
        pageSize: PAGE_SIZE,
        setQuery: (value: string) => { setQuery(value); setOffset(0); },
        setOffset,
        save,
    };
};

