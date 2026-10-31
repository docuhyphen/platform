import {submissionUnavailableReason} from "../submissionAvailability.ts";
import {Text} from "@fluentui/react-components";
import {
    InformationRequestState,
    InformationRequestSubmissionMode,
    SchemaFieldBindingDto
} from "../../../models/models.tsx";
import {RespondentPendingWork} from "../../shared/respondentPendingWork.ts";
import {AttestationExceptionSettings} from "../attestationException.ts";
import SubmissionActions from "../submission-actions/SubmissionActions.tsx";
import SubmissionAttestationCard from "../submission-attestation-card/SubmissionAttestationCard.tsx";
import SubmissionLoadError from "../submission-load-error/SubmissionLoadError.tsx";
import SubmissionPackageList from "../submission-package-list/SubmissionPackageList.tsx";
import SubmissionReadinessList from "../submission-readiness-list/SubmissionReadinessList.tsx";
import SubmissionReviewDialog from "../submission-review-dialog/SubmissionReviewDialog.tsx";
import SubmissionStagePicker from "../submission-stage-picker/SubmissionStagePicker.tsx";
import SubmissionPanelMessages from "./SubmissionPanelMessages.tsx";
import {previewReviewItems} from "../submissionReview.ts";
import {useInformationRequestSubmission} from "../useInformationRequestSubmission.ts";
import {useReviewBeforeSubmitting} from "../useReviewBeforeSubmitting.ts";
import {useInformationRequestSubmissionPanelStyles} from "./InformationRequestSubmissionPanelStyles.tsx";

interface Props
{
    requestId: string;
    requestState: InformationRequestState;
    responseETag: string;
    accessLinkToken?: string;
    requirementLabels?: Record<string, string>;
    bindings?: SchemaFieldBindingDto[];
    confirmationExceptions?: Record<string, AttestationExceptionSettings>;
    pendingWork?: RespondentPendingWork;
    onChanged: () => void;
}

const InformationRequestSubmissionPanel = ({
                                               requestId,
                                               requestState,
                                               responseETag,
                                               accessLinkToken,
                                               requirementLabels = {},
                                               bindings = [],
                                               confirmationExceptions = {},
                                               pendingWork,
                                               onChanged,
                                           }: Props) =>
{
    const styles = useInformationRequestSubmissionPanelStyles();
    const {
        preview,
        reviewed,
        selectedStageKey,
        loading,
        result,
        busy,
        error,
        retry,
        selectStage,
        submit,
        attest,
        withdraw,
        recordException,
    } =
        useInformationRequestSubmission(requestId, responseETag, accessLinkToken, onChanged, undefined, pendingWork?.evidenceRevision);
    const closed = [InformationRequestState.CLOSED, InformationRequestState.CANCELLED, InformationRequestState.SUPERSEDED, InformationRequestState.EXPIRED].includes(requestState);
    const held = busy || loading;
    const review = useReviewBeforeSubmitting(reviewed, held, pendingWork);

    if (!preview && !error) return null;

    return (
        <section id={"information-request-submission-panel"}
                 aria-labelledby={"information-request-submission-title"}
                 className={styles.panel}>
            <div id={"information-request-submission-header"}
                 className={styles.header}>
                <Text id={"information-request-submission-title"}
                      size={500}
                      weight={"semibold"}>
                    Review and submit
                </Text>
                {preview?.submissionMode === InformationRequestSubmissionMode.STAGED && selectedStageKey && (
                    <SubmissionStagePicker stageKey={selectedStageKey}
                                           stages={preview.stages}
                                           disabled={busy}
                                           onSelect={stageKey => void selectStage(stageKey)}/>
                )}
            </div>
            {!preview && error && <SubmissionLoadError message={error}
                                                       onRetry={() => void retry()}/>}
            <SubmissionPanelMessages correctionOpen={Boolean(preview && !closed && preview.correctionOpen)}
                                     error={preview ? error : null}
                                     result={result}/>
            {preview && !closed && (
                <SubmissionReadinessList ready={preview.ready}
                                         problems={preview.problems}
                                         undisclosedProblemCount={preview.undisclosedProblemCount}
                                         groupProblems={preview.groupProblems}
                                         requirementLabels={requirementLabels}/>
            )}
            {preview && !closed && preview.attestations.map(status => (
                <SubmissionAttestationCard key={status.requirementId}
                                           status={status}
                                           busy={held || pendingWork?.unsaved === true || pendingWork?.blocker != null}
                                           contactOnly={!!accessLinkToken}
                                           label={requirementLabels[status.requirementId]}
                                           exception={confirmationExceptions[status.requirementId]}
                                           onAttest={input => void attest(input)}
                                           onRecordException={input => void recordException(input)}/>
            ))}
            {preview && !closed && (
                <SubmissionActions label={preview.correctionOpen ? "Submit again" : "Submit"}
                                   disabled={held || !preview.canSubmit || review.preparing}
                                   blocker={review.blocker}
                                   unavailableReason={submissionUnavailableReason(preview)}
                                   onSubmit={review.open}/>
            )}
            {review.reviewing && (
                <SubmissionReviewDialog items={previewReviewItems(reviewed?.scopeItems ?? [], bindings)}
                                        undisclosedItemCount={reviewed?.undisclosedScopeItemCount ?? 0}
                                        busy={held || review.blocker !== null}
                                        onConfirm={() => review.confirm(() => void submit())}
                                        onDismiss={review.dismiss}/>
            )}
            {preview && (
                <SubmissionPackageList packages={preview.packages}
                                       busy={held}
                                       closed={closed}
                                       error={error}
                                       onWithdraw={withdraw}/>
            )}
        </section>
    );
};

export default InformationRequestSubmissionPanel;
