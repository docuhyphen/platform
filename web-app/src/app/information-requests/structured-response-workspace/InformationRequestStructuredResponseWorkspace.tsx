import {useEffect, useMemo, useState} from "react";
import {SaveRegular} from "@fluentui/react-icons";
import {Button, Spinner, Text} from "@fluentui/react-components";
import {InformationRequestConditionEvaluationState} from "../../models/models.tsx";
import {toFieldElementId} from "../../exchanges/components/exchange-fields-tab/fieldLayoutUtils.ts";
import {useInformationRequestStructuredResponseWorkspaceStyles} from "./InformationRequestStructuredResponseWorkspaceStyles.tsx";
import StructuredResponseOccurrence from "./StructuredResponseOccurrence.tsx";
import StructuredResponseToolbar from "./StructuredResponseToolbar.tsx";
import {
    buildResponsePatches,
    conditionScopeMap,
    ResponseEdits,
    requirementOccurrenceAnchorKey,
    ROOT_OCCURRENCE_PATH,
    siblingOccurrenceIds,
} from "./structuredResponseWorkspaceState.ts";
import {
    OccurrenceCommandResult,
    SaveResponsesResult,
    StructuredResponseWorkspaceProps,
} from "./StructuredResponseWorkspaceTypes.ts";
export type {OccurrenceCommandResult, SaveResponsesResult};
const STALE_MESSAGE = "These responses changed after this workspace loaded, so your save was not applied.";

const InformationRequestStructuredResponseWorkspace = ({
    request,
    responseETag,
    enabled,
    groups,
    occurrences,
    requirements,
    bindings,
    responses,
    onSaveResponses,
    onAddOccurrence,
    onRemoveOccurrence,
    onReorderOccurrences,
    onRefresh,
}: StructuredResponseWorkspaceProps) =>
{
    const styles = useInformationRequestStructuredResponseWorkspaceStyles();
    const [currentETag, setCurrentETag] = useState(responseETag);
    const [edits, setEdits] = useState<ResponseEdits>({});
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState<string | null>(null);

    useEffect(() => setCurrentETag(responseETag), [responseETag]);

    const conditionByScope = useMemo(
        () => conditionScopeMap(request.conditionEvaluations),
        [request.conditionEvaluations],
    );
    const shownOccurrences = useMemo(
        () =>
        {
            const hasRootRequirements = requirements.some(requirement =>
                requirementOccurrenceAnchorKey(requirement) === ROOT_OCCURRENCE_PATH);
            if (!hasRootRequirements) return occurrences;
            return [
                {
                    id: ROOT_OCCURRENCE_PATH,
                    informationRequestId: request.id,
                    sourceTemplateGroupId: ROOT_OCCURRENCE_PATH,
                    occurrenceIndex: 0,
                    occurrencePath: ROOT_OCCURRENCE_PATH,
                    createdAt: request.createdAt,
                },
                ...occurrences,
            ];
        },
        [occurrences, request.createdAt, request.id, requirements],
    );
    const rootGroups = useMemo(
        () => groups.filter(group => !group.parentGroupKey),
        [groups],
    );

    const applyResult = (result: SaveResponsesResult | OccurrenceCommandResult) =>
    {
        if (result.outcome === "STALE") setError(STALE_MESSAGE);
        else setCurrentETag(result.responseETag);
        setBusy(false);
        onRefresh();
    };

    const save = async () =>
    {
        const patches = buildResponsePatches(shownOccurrences, requirements, bindings, responses, conditionByScope, edits);
        if (patches.length === 0) return;
        setBusy(true);
        setError(null);
        applyResult(await onSaveResponses(
            request.id,
            {patches, confirmedHiddenResponseClearRequirementIds: []},
            currentETag,
        ));
    };

    if (!enabled)
    {
        return (
            <div id="information-request-response-workspace-disabled"
                 className={styles.mutedPanel}>
                Structured responses are not available.
            </div>
        );
    }

    const inactiveRules = [...new Set(
        requirements
            .map(requirement => requirement.conditionalRuleKey)
            .filter((ruleKey): ruleKey is string => Boolean(ruleKey)),
    )].filter(ruleKey => !request.conditionEvaluations.some(evaluation =>
        evaluation.ruleKey === ruleKey &&
        evaluation.state === InformationRequestConditionEvaluationState.TRUE));

    return (
        <div id="information-request-response-workspace"
             className={styles.root}>
            <StructuredResponseToolbar requestId={request.id}
                                       state={request.state}
                                       responseETag={currentETag}
                                       busy={busy}
                                       groups={rootGroups}
                                       onAddOccurrence={(requestId, groupKey, etag) =>
                                           onAddOccurrence(requestId, {groupKey}, etag)}
                                       onResult={applyResult}
                                       onCommandStart={() =>
                                       {
                                           setBusy(true);
                                           setError(null);
                                       }}/>
            {inactiveRules.map(ruleKey => (
                <Text id={`information-request-inactive-condition-${toFieldElementId(ruleKey)}`}
                      key={ruleKey}
                      className={styles.notice}>
                    {ruleKey} is inactive
                </Text>
            ))}
            <div id="information-request-response-occurrences"
                 className={styles.scrollRegion}>
                {shownOccurrences.map(occurrence =>
                {
                    const siblings = siblingOccurrenceIds(shownOccurrences, occurrence);
                    return (
                        <StructuredResponseOccurrence occurrence={occurrence}
                                                      key={occurrence.id}
                                                      occurrenceIndex={siblings.indexOf(occurrence.id)}
                                                      occurrenceCount={siblings.length}
                                                      siblingOccurrenceIds={siblings}
                                                      requestId={request.id}
                                                      responseETag={currentETag}
                                                      busy={busy}
                                                      groups={groups}
                                                      requirements={requirements}
                                                      bindings={bindings}
                                                      responses={responses}
                                                      conditionByScope={conditionByScope}
                                                      edits={edits}
                                                      setEdits={setEdits}
                                                      onAdd={(requestId, groupKey, parentOccurrenceId, etag) =>
                                                          onAddOccurrence(requestId, {groupKey, parentOccurrenceId}, etag)}
                                                      onRemove={onRemoveOccurrence}
                                                      onReorder={(
                                                          requestId,
                                                          groupKey,
                                                          parentOccurrenceId,
                                                          occurrenceIds,
                                                          etag,
                                                      ) => onReorderOccurrences(
                                                          requestId,
                                                          {groupKey, parentOccurrenceId, occurrenceIds},
                                                          etag,
                                                      )}
                                                      onResult={applyResult}/>
                    );
                })}
            </div>
            {error && (
                <Text id="information-request-response-save-error"
                      className={styles.error}>
                    {error}
                </Text>
            )}
            <div id="information-request-response-actions"
                 className={styles.actions}>
                <Button id="information-request-response-save"
                        appearance="primary"
                        shape="circular"
                        icon={busy ? <Spinner size="tiny"/> : <SaveRegular/>}
                        disabled={busy}
                        onClick={save}>
                    Save responses
                </Button>
            </div>
        </div>
    );
};

export default InformationRequestStructuredResponseWorkspace;
