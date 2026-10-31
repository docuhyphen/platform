import {useState} from "react";
import {InformationRequestOperationsRowDto, InformationRequestState} from "../../../models/models.tsx";

export const MAXIMUM_REMINDER_REQUESTS = 100;

const REMINDABLE_STATES: InformationRequestState[] = [InformationRequestState.ISSUED, InformationRequestState.IN_PROGRESS];

export const isRemindable = (row: InformationRequestOperationsRowDto): boolean => REMINDABLE_STATES.includes(row.state);

interface Selection {
    scope: string;
    ids: Set<string>;
}

export const useReminderSelection = (scope: string, rows: InformationRequestOperationsRowDto[]) => {
    const [selection, setSelection] = useState<Selection>({scope, ids: new Set()});
    const closed = new Set(rows.filter(row => !isRemindable(row)).map(row => row.requestId));
    const kept = (current: Selection): string[] =>
        current.scope === scope ? [...current.ids].filter(id => !closed.has(id)) : [];
    const selected = new Set(kept(selection));

    const toggle = (row: InformationRequestOperationsRowDto) => {
        if (!isRemindable(row)) return;
        setSelection(current => {
            const ids = new Set(kept(current));
            if (ids.has(row.requestId)) ids.delete(row.requestId);
            else if (ids.size < MAXIMUM_REMINDER_REQUESTS) ids.add(row.requestId);
            return {scope, ids};
        });
    };

    return {
        selected,
        full: selected.size >= MAXIMUM_REMINDER_REQUESTS,
        toggle,
        clear: () => setSelection({scope, ids: new Set()}),
    };
};
