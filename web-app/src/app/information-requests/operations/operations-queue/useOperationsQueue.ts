import {useCallback, useEffect, useMemo, useRef, useState} from "react";
import {useLoadedValue} from "../../../../hooks/useLoadedValue.ts";
import {sendInformationRequestReminders} from "../../../../services/informationRequestAdministrationService.ts";
import {getInformationRequestOperations} from "../../../../services/informationRequestOperationsService.ts";
import {
    InformationRequestOperationsAssigneeDto,
    InformationRequestOperationsFilter,
    InformationRequestOperationsRowDto,
    InformationRequestSlaStatus,
} from "../../../models/models.tsx";
import {informationRequestRefusalMessage} from "../../shared/informationRequestRefusal.ts";
import {OPERATIONS_CSV_FILE_NAME, operationsCsv, saveCsvFile} from "../operationsCsv.ts";
import {reminderOutcomeSentence} from "./reminderOutcome.ts";

export const OPERATIONS_PAGE_SIZE = 25;
const EXPORT_PAGE_SIZE = 200;
const SEARCH_DELAY_MS = 400;

export const useOperationsQueue = () =>
{
    const [query, setQuery] = useState("");
    const [search, setSearch] = useState<string | undefined>(undefined);
    const [assigneeId, setAssigneeId] = useState<string | undefined>(undefined);
    const [slaStatus, setSlaStatus] = useState<InformationRequestSlaStatus | undefined>(undefined);
    const [exceptionsOnly, setExceptionsOnly] = useState(false);
    const [offset, setOffset] = useState(0);
    const [selected, setSelected] = useState<Set<string>>(new Set());
    const [seen, setSeen] = useState<Map<string, InformationRequestOperationsAssigneeDto>>(new Map());
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [notice, setNotice] = useState<string | null>(null);
    const keys = useRef(new Map<string, string>());

    useEffect(() =>
    {
        const next = query.trim() || undefined;
        if (next === search) return;
        const timer = setTimeout(() =>
        {
            setSearch(next);
            setOffset(0);
        }, SEARCH_DELAY_MS);
        return () => clearTimeout(timer);
    }, [query, search]);

    const filter = useMemo<InformationRequestOperationsFilter>(
        () => ({search, assigneeId, slaStatus, exceptionsOnly, limit: OPERATIONS_PAGE_SIZE, offset}),
        [search, assigneeId, slaStatus, exceptionsOnly, offset],
    );
    const load = useCallback(() => getInformationRequestOperations(filter), [filter]);
    const page = useLoadedValue(load, "The operations queue could not be loaded.");

    useEffect(() =>
    {
        const rows = page.value?.items ?? [];
        setSeen(current =>
        {
            const unseen = rows.flatMap(row => row.assignees).filter(assignee => !current.has(assignee.principalId));
            if (unseen.length === 0) return current;
            const next = new Map(current);
            unseen.forEach(assignee => next.set(assignee.principalId, assignee));
            return next;
        });
    }, [page.value]);

    const refilter = <T>(apply: (value: T) => void) => (value: T) =>
    {
        apply(value);
        setOffset(0);
    };

    const toggle = (row: InformationRequestOperationsRowDto) => setSelected(current =>
    {
        const next = new Set(current);
        if (next.has(row.requestId)) next.delete(row.requestId);
        else next.add(row.requestId);
        return next;
    });

    const run = async (work: () => Promise<void>) =>
    {
        setBusy(true);
        setError(null);
        setNotice(null);
        try
        {
            await work();
        }
        catch (caught: unknown)
        {
            setError(informationRequestRefusalMessage(caught, "The change could not be made."));
        }
        finally
        {
            setBusy(false);
        }
    };

    const sendReminders = () => run(async () =>
    {
        const requestIds = [...selected].sort();
        const signature = `remind:${requestIds.join(",")}`;
        const key = keys.current.get(signature) ?? crypto.randomUUID();
        keys.current.set(signature, key);
        const results = await sendInformationRequestReminders(requestIds, key);
        keys.current.delete(signature);
        setNotice(reminderOutcomeSentence(results));
        setSelected(new Set());
        page.reload();
    });

    const exportCsv = () => run(async () =>
    {
        const rows: InformationRequestOperationsRowDto[] = [];
        let total = Number.POSITIVE_INFINITY;
        while (rows.length < total)
        {
            const next = await getInformationRequestOperations({...filter, limit: EXPORT_PAGE_SIZE, offset: rows.length});
            total = next.total;
            if (next.items.length === 0) break;
            rows.push(...next.items);
        }
        saveCsvFile(OPERATIONS_CSV_FILE_NAME, operationsCsv(rows));
    });

    return {
        page,
        filter,
        query,
        setQuery,
        assignees: [...seen.values()],
        setAssigneeId: refilter(setAssigneeId),
        setSlaStatus: refilter(setSlaStatus),
        setExceptionsOnly: refilter(setExceptionsOnly),
        setOffset,
        selected,
        toggle,
        busy,
        error,
        notice,
        sendReminders,
        exportCsv,
    };
};

export type OperationsQueueState = ReturnType<typeof useOperationsQueue>;
