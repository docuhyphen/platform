import {useMemo} from "react";
import {Button, Link, MessageBar, MessageBarBody} from "@fluentui/react-components";
import {
    InformationRequestExecutionStandingKind,
    InformationRequestResponseWorkspaceDto,
    InformationRequestState,
    InformationRequestTemplateRequirementDto,
} from "../../../models/models.tsx";
import InformationRequestReviewResults from "../../review/review-results/InformationRequestReviewResults.tsx";
import {requirementPromptsByKey} from "../../shared/requirementPrompts.ts";
import {RespondentPendingWork} from "../../shared/respondentPendingWork.ts";
import InformationRequestAmendmentSummary from "../amendment-summary/InformationRequestAmendmentSummary.tsx";
import {attestationExceptionsOf} from "../attestationException.ts";
import InformationRequestCarryForwardList from "../carry-forward-list/InformationRequestCarryForwardList.tsx";
import {requirementValueFormats} from "../carry-forward-list/carryForwardFormatting.ts";
import InformationRequestSubmissionPanel from "../submission-panel/InformationRequestSubmissionPanel.tsx";
import InformationRequestSupplementDialog from "../supplement-dialog/InformationRequestSupplementDialog.tsx";
import {useInformationRequestSubmissionSectionStyles} from "./InformationRequestSubmissionSectionStyles.tsx";
import {requirementLabelsOf} from "./requirementLabels.ts";
import {useSupplementRequest} from "./useSupplementRequest.ts";

interface Props
{
    workspace: InformationRequestResponseWorkspaceDto;
    requirements: InformationRequestTemplateRequirementDto[];
    accessLinkToken?: string;
    pendingWork?: RespondentPendingWork;
    onChanged: () => void;
}

const FOLLOW_UP_STATES = new Set([InformationRequestState.ISSUED, InformationRequestState.IN_PROGRESS, InformationRequestState.CLOSED]);

const InformationRequestSubmissionSection = ({
                                                 workspace,
                                                 requirements,
                                                 accessLinkToken,
                                                 pendingWork,
                                                 onChanged
                                             }: Props) =>
{
    const styles = useInformationRequestSubmissionSectionStyles();
    const request = workspace.request;
    const supplement = useSupplementRequest(request, onChanged);
    const requirementLabels = useMemo(
        () => requirementLabelsOf(workspace.responses, requirements),
        [requirements, workspace.responses],
    );
    const confirmationExceptions = useMemo(
        () => attestationExceptionsOf(requirements, workspace.responses),
        [requirements, workspace.responses],
    );
    const bindings = workspace.schemaAssignment?.bindings;
    const valueFormats = useMemo(
        () => requirementValueFormats(workspace.responses, requirements, bindings ?? []),
        [bindings, requirements, workspace.responses],
    );
    const canRequestSupplement = !accessLinkToken &&
        workspace.canRequestSupplement === true &&
        workspace.executionStanding.kind === InformationRequestExecutionStandingKind.ACTIVE &&
        FOLLOW_UP_STATES.has(request.state);

    return (
        <div id={"information-request-submission-section"}
             className={styles.section}>
            <InformationRequestAmendmentSummary requestId={request.id}
                                                accessLinkToken={accessLinkToken}
                                                refreshKey={workspace.responseETag}
                                                prompts={requirementPromptsByKey(workspace.templateVersion.sections)}/>
            <InformationRequestCarryForwardList requestId={request.id}
                                                accessLinkToken={accessLinkToken}
                                                requirementLabels={requirementLabels}
                                                valueFormats={valueFormats}/>
            <InformationRequestSubmissionPanel requestId={request.id}
                                               requestState={request.state}
                                               responseETag={workspace.responseETag}
                                               accessLinkToken={accessLinkToken}
                                               requirementLabels={requirementLabels}
                                               bindings={workspace.schemaAssignment?.bindings ?? []}
                                               confirmationExceptions={confirmationExceptions}
                                               pendingWork={pendingWork}
                                               onChanged={onChanged}/>
            <InformationRequestReviewResults requestId={request.id}
                                             accessLinkToken={accessLinkToken}
                                             refreshKey={workspace.responseETag}
                                             requirementLabels={requirementLabels}
                                             onChanged={onChanged}/>
            {supplement.message && (!supplement.dialogOpen || supplement.message.intent === "success") && (
                <MessageBar id={"information-request-supplement-message"}
                            intent={supplement.message.intent}>
                    <MessageBarBody
                        id={"information-request-supplement-message-body"}>{supplement.message.text}</MessageBarBody>
                </MessageBar>
            )}
            {supplement.successorId && (
                <Link id={"information-request-supplement-open"}
                      href={`/information-requests/${supplement.successorId}/manage`}>
                    Open supplemental draft
                </Link>
            )}
            {canRequestSupplement && (
                <div id={"information-request-follow-up-actions"}
                     className={styles.actions}>
                    <Button id={"information-request-request-supplement"}
                            appearance={"secondary"}
                            shape={"circular"}
                            disabled={supplement.busy}
                            onClick={supplement.openDialog}>
                        Request more information
                    </Button>
                </div>
            )}
            {supplement.dialogOpen && (
                <InformationRequestSupplementDialog busy={supplement.busy}
                                                    error={supplement.message?.intent === "error" ? supplement.message.text : null}
                                                    onConfirm={reason => void supplement.request(reason)}
                                                    onDismiss={supplement.closeDialog}/>
            )}
        </div>
    );
};

export default InformationRequestSubmissionSection;
