import {useCallback, useState} from "react";
import {useLoadedValue} from "../../../../hooks/useLoadedValue.ts";
import {
    InformationRequestAuditSearch,
    searchInformationRequestAuditEvents,
} from "../../../../services/informationRequestAdministrationService.ts";
import {instantFromLocalInput} from "../../shared/dateTimeInput.ts";

export const AUDIT_SEARCH_PAGE_SIZE = 50;

export interface AuditSearchForm
{
    requestId: string;
    eventClass: string;
    eventType: string;
    actorId: string;
    from: string;
    until: string;
}

const EMPTY_FORM: AuditSearchForm = {requestId: "", eventClass: "", eventType: "", actorId: "", from: "", until: ""};

type AuditCriteria = Omit<InformationRequestAuditSearch, "limit" | "offset">;

const criteriaOf = (form: AuditSearchForm): AuditCriteria =>
{
    const requestId = form.requestId.trim();
    const eventClass = form.eventClass.trim();
    const eventType = form.eventType.trim();
    const actorId = form.actorId.trim();
    const occurredAfter = instantFromLocalInput(form.from);
    const occurredBefore = instantFromLocalInput(form.until);
    return {
        ...(requestId ? {requestId} : {}),
        ...(eventClass ? {eventClass} : {}),
        ...(eventType ? {eventType} : {}),
        ...(actorId ? {actorId} : {}),
        ...(occurredAfter ? {occurredAfter} : {}),
        ...(occurredBefore ? {occurredBefore} : {}),
    };
};

export const periodProblem = (form: AuditSearchForm): string | undefined =>
{
    const after = instantFromLocalInput(form.from);
    const before = instantFromLocalInput(form.until);
    return after && before && new Date(before) <= new Date(after) ? "The period ends after it starts." : undefined;
};

export const useAuditSearch = () =>
{
    const [form, setForm] = useState<AuditSearchForm>(EMPTY_FORM);
    const [criteria, setCriteria] = useState<AuditCriteria>({});
    const [offset, setOffset] = useState(0);
    const load = useCallback(
        () => searchInformationRequestAuditEvents({...criteria, limit: AUDIT_SEARCH_PAGE_SIZE, offset}),
        [criteria, offset],
    );
    const page = useLoadedValue(load, "The audit record could not be searched.");

    return {
        form,
        problem: periodProblem(form),
        page,
        offset,
        change: (field: keyof AuditSearchForm, value: string) => setForm(current => ({...current, [field]: value})),
        search: () =>
        {
            setCriteria(criteriaOf(form));
            setOffset(0);
        },
        setOffset,
    };
};
