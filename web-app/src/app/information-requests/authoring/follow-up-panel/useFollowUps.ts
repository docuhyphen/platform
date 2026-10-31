import * as authoring from "../../../../services/informationRequestAuthoringService.ts";
import {
    DefineInformationRequestRecurrenceRequest,
    DefineInformationRequestRefreshRuleRequest,
    InformationRequestDto,
    InformationRequestRecurrenceDto,
} from "../../../models/models.tsx";
import {informationRequestRefusalMessage} from "../../shared/informationRequestRefusal.ts";
import {useServerRead} from "../../shared/useServerRead.ts";
import {useAuthorCommandRunner} from "../author-workspace/useAuthorCommandRunner.ts";
import {nextOccurrenceReady} from "./followUpAvailability.ts";

const LINEAGE_FAILURE = "The follow-up requests could not be loaded.";

export const useFollowUps = (request: InformationRequestDto, onChanged: () => void) =>
{
    const read = useServerRead(
        request.id,
        () => authoring.getInformationRequestLineage(request.id),
        refusal => informationRequestRefusalMessage(refusal, LINEAGE_FAILURE),
    );

    const runner = useAuthorCommandRunner(request.id, async () =>
    {
        await read.reload();
        onChanged();
    });

    const lineage = read.value;

    return {
        lineage,
        loadError: read.error,
        reload: read.reload,
        busy: runner.busy,
        error: runner.error,
        notice: runner.notice,
        nextOccurrenceReady: nextOccurrenceReady(lineage, Date.now()),
        schedule: (definition: DefineInformationRequestRecurrenceRequest) =>
            runner.run(`recur:${request.requestETag}:${JSON.stringify(definition)}`, key =>
                authoring.defineInformationRequestRecurrence(request.id, definition, request.requestETag, key), "The recurrence was scheduled."),
        createNext: (recurrence: InformationRequestRecurrenceDto) =>
            runner.run(`next:${recurrence.id}:${lineage?.nextOccurrenceDueAt ?? ""}`, key =>
                    authoring.createNextInformationRequestOccurrence(recurrence.originRequestId, recurrence.id, key),
                "The next request was created."),
        supplement: (reasonCode: string) =>
            runner.run(`supplement:${request.requestETag}:${reasonCode}`, key =>
                authoring.requestInformationRequestSupplement(request.id, reasonCode, request.requestETag, key), "The supplement was created."),
        defineRefreshRule: (definition: DefineInformationRequestRefreshRuleRequest) =>
            runner.run(`refresh-rule:${request.requestETag}:${JSON.stringify(definition)}`, key =>
                authoring.defineInformationRequestRefreshRule(request.id, definition, request.requestETag, key), "The refresh rule was added."),
        refresh: (refreshRuleId: string) =>
            runner.run(`refresh:${refreshRuleId}`, key =>
                authoring.createInformationRequestRefresh(request.id, refreshRuleId, key), "The refreshed request was created."),
    };
};
