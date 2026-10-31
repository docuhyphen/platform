import {useEffect, useState} from "react";
import {LoadedValue} from "../../../../hooks/useLoadedValue.ts";
import {InformationRequestOperationsAssigneeDto, InformationRequestOperationsPageDto} from "../../../models/models.tsx";

export const useLearnedAssignees = (page: LoadedValue<InformationRequestOperationsPageDto>): InformationRequestOperationsAssigneeDto[] => {
    const [seen, setSeen] = useState<Map<string, InformationRequestOperationsAssigneeDto>>(new Map());
    const [refused, setRefused] = useState(false);

    useEffect(() => {
        if (page.error) setRefused(true);
        else if (page.value) setRefused(false);
    }, [page.error, page.value]);

    useEffect(() => {
        const rows = page.value?.items ?? [];
        setSeen(current => {
            const unseen = rows.flatMap(row => row.assignees).filter(assignee => !current.has(assignee.principalId));
            if (unseen.length === 0) return current;
            const next = new Map(current);
            unseen.forEach(assignee => next.set(assignee.principalId, assignee));
            return next;
        });
    }, [page.value]);

    return page.error || refused ? [] : [...seen.values()];
};
