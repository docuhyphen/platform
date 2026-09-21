import {InformationRequestConditionEvaluationDto} from "../../models/models.tsx";
import {
    InformationRequestGroupOccurrenceDto,
    InformationRequestResponseDto,
    InformationRequestTemplateGroupDto,
    InformationRequestTemplateRequirementDto,
    SchemaFieldBindingDto,
} from "../../models/models.tsx";
import {useInformationRequestStructuredResponseWorkspaceStyles} from "./InformationRequestStructuredResponseWorkspaceStyles.tsx";
import StructuredResponseOccurrence from "./StructuredResponseOccurrence.tsx";
import {
    ResponseEdits,
    siblingOccurrenceIds,
} from "./structuredResponseWorkspaceState.ts";
import {OccurrenceCommandResult} from "./StructuredResponseWorkspaceTypes.ts";

interface Props
{
    occurrences: InformationRequestGroupOccurrenceDto[];
    requestId: string;
    responseETag: string;
    busy: boolean;
    groups: InformationRequestTemplateGroupDto[];
    requirements: InformationRequestTemplateRequirementDto[];
    bindings: SchemaFieldBindingDto[];
    responses: InformationRequestResponseDto[];
    conditionByScope: Map<string, InformationRequestConditionEvaluationDto>;
    edits: ResponseEdits;
    setEdits: (edits: (previous: ResponseEdits) => ResponseEdits) => void;
    onAddOccurrence: (
        requestId: string,
        request: {groupKey: string; parentOccurrenceId?: string},
        responseETag: string,
    ) => Promise<OccurrenceCommandResult>;
    onRemoveOccurrence: (
        requestId: string,
        occurrenceId: string,
        responseETag: string,
    ) => Promise<OccurrenceCommandResult>;
    onReorderOccurrences: (
        requestId: string,
        request: {groupKey: string; parentOccurrenceId?: string; occurrenceIds: string[]},
        responseETag: string,
    ) => Promise<OccurrenceCommandResult>;
    onResult: (result: OccurrenceCommandResult) => void;
    onCommandStart: () => void;
    onCommandFailure: (error: unknown) => void;
}

const StructuredResponseOccurrenceList = ({
    occurrences,
    requestId,
    responseETag,
    busy,
    groups,
    requirements,
    bindings,
    responses,
    conditionByScope,
    edits,
    setEdits,
    onAddOccurrence,
    onRemoveOccurrence,
    onReorderOccurrences,
    onResult,
    onCommandStart,
    onCommandFailure,
}: Props) =>
{
    const styles = useInformationRequestStructuredResponseWorkspaceStyles();

    return (
        <div id="information-request-response-occurrences"
             className={styles.scrollRegion}>
            {occurrences.map(occurrence =>
            {
                const siblings = siblingOccurrenceIds(occurrences, occurrence);
                return (
                    <StructuredResponseOccurrence occurrence={occurrence}
                                                  key={occurrence.id}
                                                  occurrenceIndex={siblings.indexOf(occurrence.id)}
                                                  occurrenceCount={siblings.length}
                                                  siblingOccurrenceIds={siblings}
                                                  requestId={requestId}
                                                  responseETag={responseETag}
                                                  busy={busy}
                                                  groups={groups}
                                                  requirements={requirements}
                                                  bindings={bindings}
                                                  responses={responses}
                                                  conditionByScope={conditionByScope}
                                                  edits={edits}
                                                  setEdits={setEdits}
                                                  onAdd={(targetRequestId, groupKey, parentOccurrenceId, etag) =>
                                                      onAddOccurrence(targetRequestId, {groupKey, parentOccurrenceId}, etag)}
                                                  onRemove={onRemoveOccurrence}
                                                  onReorder={(
                                                      targetRequestId,
                                                      groupKey,
                                                      parentOccurrenceId,
                                                      occurrenceIds,
                                                      etag,
                                                  ) => onReorderOccurrences(
                                                      targetRequestId,
                                                      {groupKey, parentOccurrenceId, occurrenceIds},
                                                      etag,
                                                  )}
                                                  onResult={onResult}
                                                  onCommandStart={onCommandStart}
                                                  onCommandFailure={onCommandFailure}/>
                );
            })}
        </div>
    );
};

export default StructuredResponseOccurrenceList;
