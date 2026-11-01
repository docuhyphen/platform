import {useCallback, useEffect, useRef, useState} from "react";
import {AuditEventCursorDto, AuditEventDto, AuditEventPageDto} from "../../../models/models.tsx";
import {AuditCursorParams} from "../../../../services/auditService.ts";
import {normalizeApiError} from "../../../../utils/apiErrorUtils.ts";

export interface UseAuditEventPageResult
{
    items: AuditEventDto[];
    loading: boolean;
    error: string | null;
    cursor: AuditEventCursorDto | null;
    loadMore: () => void;
    reset: () => void;
}

export const useAuditEventPage = (
    fetchFn: (params?: AuditCursorParams) => Promise<AuditEventPageDto>,
): UseAuditEventPageResult =>
{
    const [items, setItems] = useState<AuditEventDto[]>([]);
    const [cursor, setCursor] = useState<AuditEventCursorDto | null>(null);
    const [loading, setLoading] = useState<boolean>(true);
    const [error, setError] = useState<string | null>(null);
    const fetchFnRef = useRef(fetchFn);
    fetchFnRef.current = fetchFn;
    const requestGenerationRef = useRef(0);

    const fetchPage = useCallback(async (params: AuditCursorParams | undefined, append: boolean) =>
    {
        const generation = ++requestGenerationRef.current;
        setLoading(true);
        setError(null);

        try
        {
            const page = await fetchFnRef.current(params);
            if (generation !== requestGenerationRef.current)
            {
                return;
            }
            setItems((previous) => append ? [...previous, ...page.items] : page.items);
            setCursor(page.nextCursor);
        }
        catch (err: unknown)
        {
            if (generation !== requestGenerationRef.current)
            {
                return;
            }
            setError(normalizeApiError(err, "Failed to load audit events.").message);
        }
        finally
        {
            if (generation === requestGenerationRef.current)
            {
                setLoading(false);
            }
        }
    }, []);

    useEffect(() =>
    {
        fetchPage(undefined, false);
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, []);

    const loadMore = useCallback(() =>
    {
        if (!cursor || loading)
        {
            return;
        }

        fetchPage({cursorOccurredAt: cursor.occurredAt, cursorEventId: cursor.eventId}, true);
    }, [cursor, loading, fetchPage]);

    const reset = useCallback(() =>
    {
        setItems([]);
        setCursor(null);
        fetchPage(undefined, false);
    }, [fetchPage]);

    return {items, loading, error, cursor, loadMore, reset};
};
