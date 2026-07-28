import {useCallback, useEffect, useState} from "react";
import {fetchPlatformOrganizations, PlatformApiError} from "../../../services/platformOrganizationApi.ts";
import {PlatformOrganizationSummary} from "../../../services/types/platformOrganizations.ts";

const PAGE_SIZE = 20;

export const usePlatformOrganizations = () =>
{
    const [organizations, setOrganizations] = useState<PlatformOrganizationSummary[]>([]);
    const [query, setQuery] = useState("");
    const [status, setStatus] = useState<"ALL" | "ACTIVE" | "INACTIVE">("ALL");
    const [tierCode, setTierCode] = useState("");
    const [offset, setOffset] = useState(0);
    const [total, setTotal] = useState(0);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);
    const [noAccess, setNoAccess] = useState(false);
    const [selected, setSelected] = useState<PlatformOrganizationSummary | null>(null);

    const loadOrganizations = useCallback(async () =>
    {
        setLoading(true);
        setError(null);
        setNoAccess(false);
        try
        {
            const result = await fetchPlatformOrganizations({
                query: query.trim() || undefined,
                status,
                tierCode: tierCode.trim() || undefined,
                sort: "name",
                direction: "asc",
                limit: PAGE_SIZE,
                offset,
            });
            setOrganizations(result.items);
            setTotal(result.total);
        }
        catch (loadError: unknown)
        {
            const apiError = loadError as PlatformApiError;
            if (apiError.status === 403)
            {
                setNoAccess(true);
            }
            else
            {
                setError(apiError.errorMessage || "Failed to load organizations");
            }
        }
        finally
        {
            setLoading(false);
        }
    }, [offset, query, status, tierCode]);

    useEffect(() =>
    {
        void loadOrganizations();
    }, [loadOrganizations]);

    const applyFilters = (nextQuery: string, nextStatus: "ALL" | "ACTIVE" | "INACTIVE", nextTier: string) =>
    {
        setOffset(0);
        setQuery(nextQuery);
        setStatus(nextStatus);
        setTierCode(nextTier);
    };

    return {
        organizations,
        total,
        offset,
        pageSize: PAGE_SIZE,
        loading,
        error,
        noAccess,
        selected,
        setOffset,
        setSelected,
        applyFilters,
        reload: loadOrganizations,
    };
};
