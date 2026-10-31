import {Dispatch, SetStateAction, useEffect, useMemo, useState} from "react";
import {
    InformationRequestOperationsException,
    InformationRequestOperationsFilter,
    InformationRequestSlaStatus,
    InformationRequestState,
} from "../../../models/models.tsx";

export const OPERATIONS_PAGE_SIZE = 25;
const SEARCH_DELAY_MS = 400;

export const OPEN_REQUEST_STATES: InformationRequestState[] = [
    InformationRequestState.DRAFT,
    InformationRequestState.ISSUED,
    InformationRequestState.IN_PROGRESS,
];

export const useOperationsQueueFilter = () => {
    const [query, setQuery] = useState("");
    const [search, setSearch] = useState<string | undefined>(undefined);
    const [states, setStates] = useState<InformationRequestState[]>(OPEN_REQUEST_STATES);
    const [exchangeId, setExchangeId] = useState<string | undefined>(undefined);
    const [assigneeId, setAssigneeId] = useState<string | undefined>(undefined);
    const [slaStatus, setSlaStatus] = useState<InformationRequestSlaStatus | undefined>(undefined);
    const [exceptions, setExceptions] = useState<InformationRequestOperationsException[]>([]);
    const [exceptionsOnly, setExceptionsOnly] = useState(false);
    const [offset, setOffset] = useState(0);

    useEffect(() => {
        const next = query.trim() || undefined;
        if (next === search) return;
        const timer = setTimeout(() => {
            setSearch(next);
            setOffset(0);
        }, SEARCH_DELAY_MS);
        return () => clearTimeout(timer);
    }, [query, search]);

    const filter = useMemo<InformationRequestOperationsFilter>(
        () => ({
            states,
            exchangeId,
            search,
            assigneeId,
            slaStatus,
            exceptions,
            exceptionsOnly,
            limit: OPERATIONS_PAGE_SIZE,
            offset
        }),
        [states, exchangeId, search, assigneeId, slaStatus, exceptions, exceptionsOnly, offset],
    );

    const refilter = <T>(apply: Dispatch<SetStateAction<T>>) => (value: T) => {
        apply(value);
        setOffset(0);
    };

    return {
        filter,
        query,
        setQuery,
        setStates: refilter(setStates),
        setExchangeId: refilter(setExchangeId),
        setAssigneeId: refilter(setAssigneeId),
        setSlaStatus: refilter(setSlaStatus),
        setExceptions: refilter(setExceptions),
        setExceptionsOnly: refilter(setExceptionsOnly),
        setOffset,
    };
};
