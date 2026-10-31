import {Text} from "@fluentui/react-components";
import {
    useInformationRequestStructuredResponseWorkspaceStyles
} from "./InformationRequestStructuredResponseWorkspaceStyles.tsx";
import StructuredResponseConflictReview
    from "./structured-response-conflict-review/StructuredResponseConflictReview.tsx";
import StructuredResponseHiddenClearConfirmations
    from "./structured-response-hidden-clear-confirmations/StructuredResponseHiddenClearConfirmations.tsx";
import StructuredResponseOccurrenceList
    from "./structured-response-occurrence-list/StructuredResponseOccurrenceList.tsx";
import StructuredResponseSaveFooter from "./structured-response-save-footer/StructuredResponseSaveFooter.tsx";
import StructuredResponseToolbar from "./structured-response-toolbar/StructuredResponseToolbar.tsx";
import {
    OccurrenceCommandResult,
    SaveResponsesResult,
    StructuredResponseWorkspaceProps,
} from "./StructuredResponseWorkspaceTypes.ts";
import {useStructuredResponseWorkspaceController} from "./useStructuredResponseWorkspaceController.ts";
import {SupportingEvidenceContext} from "./supportingEvidenceNotes.ts";

export type {OccurrenceCommandResult, SaveResponsesResult};

const StructuredResponseWorkspaceContent = (props: StructuredResponseWorkspaceProps) =>
{
    const {
        request,
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
    const nothingToAnswer = responses.length === 0 && controller.rootGroups.length === 0;

    return (
        <SupportingEvidenceContext.Provider value={controller.supportingNotes}>
            <div id="information-request-response-workspace"
                 className={styles.root}>
                <StructuredResponseToolbar requestId={request.id}
                                           state={request.state}
                                           responseETag={controller.currentETag}
                                           busy={controller.busy}
                                           readOnly={controller.readOnly}
                                           groups={controller.rootGroups}
                                           occurrences={controller.shownOccurrences}
                                           onAddOccurrence={(requestId, groupKey, etag) =>
                                               onAddOccurrence(requestId, {groupKey}, etag)}
                                           onResult={controller.applyResult}
                                           onCommandStart={controller.applyCommandStart}
                                           onCommandFailure={controller.applyCommandFailure}/>
                <StructuredResponseHiddenClearConfirmations confirmations={controller.clearConfirmations}
                                                            confirmedRequirementIds={controller.confirmedHiddenClearIds}
                                                            focusRequest={controller.clearsFocusRequest}
                                                            labelOf={controller.labelOf}
                                                            onToggle={controller.toggleHiddenClearConfirmation}/>
                <StructuredResponseConflictReview conflicts={controller.conflicts}
                                                  onKeepSaved={controller.keepSavedAnswer}/>
                {nothingToAnswer
                    ? (
                        <Text id="information-request-response-nothing-to-answer"
                              className={styles.nothing}>
                            There is nothing for you to answer here right now.
                        </Text>
                    )
                    : (
                        <>
                            <StructuredResponseOccurrenceList occurrences={controller.shownOccurrences}
                                                              requestId={request.id}
                                                              responseETag={controller.currentETag}
                                                              busy={controller.busy}
                                                              readOnly={controller.readOnly}
                                                              groups={groups}
                                                              creatable={controller.creatable}
                                                              requirements={requirements}
                                                              bindings={bindings}
                                                              responses={responses}
                                                              conditionByScope={controller.conditionByScope}
                                                              edits={controller.edits}
                                                              setEdits={controller.setEdits}
                                                              answers={controller.answers}
                                                              setAnswers={controller.setAnswers}
                                                              onAddOccurrence={onAddOccurrence}
                                                              onRemoveOccurrence={onRemoveOccurrence}
                                                              onReorderOccurrences={onReorderOccurrences}
                                                              onResult={controller.applyResult}
                                                              onCommandStart={controller.applyCommandStart}
                                                              onCommandFailure={controller.applyCommandFailure}/>
                            <StructuredResponseSaveFooter busy={controller.busy}
                                                          readOnly={controller.readOnly}
                                                          error={controller.error}
                                                          refusedItems={controller.refusedItems}
                                                          status={controller.status}
                                                          onSave={() => void controller.save()}/>
                        </>
                    )}
            </div>
        </SupportingEvidenceContext.Provider>
    );
};

const InformationRequestStructuredResponseWorkspace = (props: StructuredResponseWorkspaceProps) => (
    <StructuredResponseWorkspaceContent key={`${props.request.id}|${props.respondentKey}`}
                                        {...props}/>
);

export default InformationRequestStructuredResponseWorkspace;
