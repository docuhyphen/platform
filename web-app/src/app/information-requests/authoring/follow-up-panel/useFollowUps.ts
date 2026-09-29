import {useCallback, useEffect, useState} from "react";
import * as authoring from "../../../../services/informationRequestAuthoringService.ts";
import {
    DefineInformationRequestRecurrenceRequest,
    InformationRequestDto,
    InformationRequestLineageViewDto,
} from "../../../models/models.tsx";
import {useAuthorCommandRunner} from "../author-workspace/useAuthorCommandRunner.ts";

export const useFollowUps = (request: InformationRequestDto, onChanged: () => void) =>
{
    const [lineage, setLineage] = useState<InformationRequestLineageViewDto | null>(null);

    const load = useCallback(async () =>
    {
        try
        {
            setLineage(await authoring.getInformationRequestLineage(request.id));
        }
        catch
        {
            setLineage({informationRequestId: request.id, successors: []});
        }
    }, [request.id]);

    useEffect(() =>
    {
        void load();
    }, [load]);

    const runner = useAuthorCommandRunner(async () =>
    {
        await load();
        onChanged();
    });

    const nextDue = lineage?.nextOccurrenceDueAt ? new Date(lineage.nextOccurrenceDueAt) : null;

    return {
        lineage,
        busy: runner.busy,
        error: runner.error,
        notice: runner.notice,
        nextOccurrenceReady: Boolean(nextDue && nextDue.getTime() <= Date.now()),
        schedule: (definition: DefineInformationRequestRecurrenceRequest) =>
            runner.run(`recur:${request.requestETag}:${JSON.stringify(definition)}`, key =>
                authoring.defineInformationRequestRecurrence(request.id, definition, request.requestETag, key), "The recurrence was scheduled."),
        createNext: (recurrenceId: string) =>
            runner.run(`next:${recurrenceId}:${lineage?.nextOccurrenceDueAt ?? ""}`, key =>
                authoring.createNextInformationRequestOccurrence(request.id, recurrenceId, key), "The next request was created."),
        supplement: (reasonCode: string) =>
            runner.run(`supplement:${request.requestETag}:${reasonCode}`, key =>
                authoring.requestInformationRequestSupplement(request.id, reasonCode, request.requestETag, key), "The supplement was created."),
    };
};
