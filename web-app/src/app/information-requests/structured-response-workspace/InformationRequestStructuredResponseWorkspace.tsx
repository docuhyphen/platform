import {useInformationRequestStructuredResponseWorkspaceStyles} from "./InformationRequestStructuredResponseWorkspaceStyles.tsx";
import StructuredResponseHiddenClearConfirmations from "./StructuredResponseHiddenClearConfirmations.tsx";
import StructuredResponseInactiveConditionNotices from "./StructuredResponseInactiveConditionNotices.tsx";
import StructuredResponseOccurrenceList from "./StructuredResponseOccurrenceList.tsx";
import StructuredResponseSaveFooter from "./StructuredResponseSaveFooter.tsx";
import StructuredResponseToolbar from "./StructuredResponseToolbar.tsx";
import {
    OccurrenceCommandResult,
    SaveResponsesResult,
    StructuredResponseWorkspaceProps,
} from "./StructuredResponseWorkspaceTypes.ts";
import {useStructuredResponseWorkspaceController} from "./useStructuredResponseWorkspaceController.ts";
export type {OccurrenceCommandResult, SaveResponsesResult};

const InformationRequestStructuredResponseWorkspace = (props: StructuredResponseWorkspaceProps) =>
{
    const {
        request,
        enabled,
        groups,
        requirements,
        bindings,
        responses,
        onAddOccurrence,
        onRemoveOccurrence,
        onReorderOccurrences,
    } = props;
    const styles = useInformationRequestStructuredResponseWorkspaceStyles();
    const controller = useStructuredResponseWorkspaceController(props);

    if (!enabled)
    {
        return (
            <div id="information-request-response-workspace-disabled"
                 className={styles.mutedPanel}>
                Structured responses are not available.
            </div>
        );
    }

    return (
        <div id="information-request-response-workspace"
             className={styles.root}>
            <StructuredResponseToolbar requestId={request.id}
                                       state={request.state}
                                       responseETag={controller.currentETag}
                                       busy={controller.busy}
                                       groups={controller.rootGroups}
                                       onAddOccurrence={(requestId, groupKey, etag) =>
                                           onAddOccurrence(requestId, {groupKey}, etag)}
                                       onResult={controller.applyResult}
                                       onCommandStart={controller.applyCommandStart}
                                       onCommandFailure={controller.applyCommandFailure}/>
            <StructuredResponseInactiveConditionNotices requirements={requirements}
                                                      conditionEvaluations={request.conditionEvaluations}/>
            <StructuredResponseHiddenClearConfirmations confirmations={controller.clearConfirmations}
                                                       confirmedRequirementIds={controller.confirmedHiddenClearIds}
                                                       onToggle={controller.toggleHiddenClearConfirmation}/>
            <StructuredResponseOccurrenceList occurrences={controller.shownOccurrences}
                                              requestId={request.id}
                                              responseETag={controller.currentETag}
                                              busy={controller.busy}
                                              groups={groups}
                                              requirements={requirements}
                                              bindings={bindings}
                                              responses={responses}
                                              conditionByScope={controller.conditionByScope}
                                              edits={controller.edits}
                                              setEdits={controller.setEdits}
                                              onAddOccurrence={onAddOccurrence}
                                              onRemoveOccurrence={onRemoveOccurrence}
                                              onReorderOccurrences={onReorderOccurrences}
                                              onResult={controller.applyResult}
                                              onCommandStart={controller.applyCommandStart}
                                              onCommandFailure={controller.applyCommandFailure}/>
            <StructuredResponseSaveFooter busy={controller.busy}
                                          error={controller.error}
                                          onSave={controller.save}/>
        </div>
    );
};

export default InformationRequestStructuredResponseWorkspace;
