import {useCallback, useEffect, useRef, useState} from "react";
import {AuditEventCursorDto, AuditEventDto, AuditEventPageDto} from "../../../models/models.tsx";
import {AuditCursorParams} from "../../../../services/auditService.ts";

export interface UseAuditEventPageResult
{
    items: AuditEventDto[];
    loading: boolean;
    error: string | null;
    cursor: AuditEventCursorDto | null;
    loadMore: () => void;
    reset: () => void;
}

/**
 * Cursor-paginated audit event list state, shared by every ledger-backed audit surface
 * (Audit workspace, Exchange audit tab/document audit, personal security activity). `fetchFn`
 * is one of the typed functions in `services/auditService.ts`.
 */
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

    const fetchPage = useCallback(async (params: AuditCursorParams | undefined, append: boolean) =>
    {
        setLoading(true);
        setError(null);

        try
        {
            const page = await fetchFnRef.current(params);
            setItems((previous) => append ? [...previous, ...page.items] : page.items);
            setCursor(page.nextCursor);
        }
        catch (err: unknown)
        {
            const message = err instanceof Error ? err.message : "Failed to load audit events.";
            setError(message);
        }
        finally
        {
            setLoading(false);
        }
    }, []);

    useEffect(() =>
    {
        fetchPage(undefined, false);
        // Runs once on mount; callers use `reset` to force a refetch with a new fetchFn identity.
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
