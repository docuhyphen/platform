import {useMemo, useState} from "react";
import {Button, MessageBar, MessageBarBody} from "@fluentui/react-components";
import {createInformationRequestSupplement} from "../../../../services/informationRequestSubmissionService.ts";
import {
    InformationRequestExecutionStandingKind,
    InformationRequestResponseWorkspaceDto,
    InformationRequestState,
    InformationRequestTemplateRequirementDto,
} from "../../../models/models.tsx";
import InformationRequestReviewResults from "../../review/review-results/InformationRequestReviewResults.tsx";
import InformationRequestAmendmentSummary from "../amendment-summary/InformationRequestAmendmentSummary.tsx";
import InformationRequestCarryForwardList from "../carry-forward-list/InformationRequestCarryForwardList.tsx";
import InformationRequestSubmissionPanel from "../submission-panel/InformationRequestSubmissionPanel.tsx";
import InformationRequestSupplementDialog from "../supplement-dialog/InformationRequestSupplementDialog.tsx";
import {submissionErrorMessage} from "../submissionLabels.ts";
import {submissionReviewItems} from "../submissionReview.ts";
import {useInformationRequestSubmissionSectionStyles} from "./InformationRequestSubmissionSectionStyles.tsx";

interface Props
{
    workspace: InformationRequestResponseWorkspaceDto;
    requirements: InformationRequestTemplateRequirementDto[];
    accessLinkToken?: string;
    onChanged: () => void;
}

const FOLLOW_UP_STATES = new Set([InformationRequestState.ISSUED, InformationRequestState.IN_PROGRESS, InformationRequestState.CLOSED]);

const InformationRequestSubmissionSection = ({workspace, requirements, accessLinkToken, onChanged}: Props) =>
{
    const styles = useInformationRequestSubmissionSectionStyles();
    const [dialogOpen, setDialogOpen] = useState(false);
    const [busy, setBusy] = useState(false);
    const [message, setMessage] = useState<{intent: "success" | "error"; text: string} | null>(null);
    const request = workspace.request;
    const requirementLabels = useMemo(() =>
    {
        const prompts = new Map(requirements.map(requirement => [requirement.id, requirement.prompt]));
        return Object.fromEntries(workspace.responses.map(response =>
            [response.informationRequestRequirementId, prompts.get(response.sourceTemplateBindingId) ?? "Requested item"]));
    }, [requirements, workspace.responses]);
    const reviewItems = useMemo(() => submissionReviewItems(workspace.responses, requirements), [requirements, workspace.responses]);
    const canRequestSupplement = !accessLinkToken &&
        workspace.executionStanding.kind === InformationRequestExecutionStandingKind.ACTIVE &&
        FOLLOW_UP_STATES.has(request.state);

    const requestSupplement = async (reason: string) =>
    {
        setBusy(true);
        try
        {
            const outcome = await createInformationRequestSupplement(request.id, reason || undefined, {
                expectedETag: request.requestETag,
                idempotencyKey: crypto.randomUUID(),
            });
            setMessage(outcome.outcome === "SAVED"
                ? {intent: "success", text: "A supplemental request was created as a draft for the same parties."}
                : {intent: "error", text: "This request changed. Reload it before asking for more information."});
            setDialogOpen(false);
        }
        catch (caught: unknown)
        {
            setMessage({intent: "error", text: submissionErrorMessage(caught, "The supplemental request could not be created.")});
        }
        finally
        {
            setBusy(false);
        }
    };

    return (
        <div id={"information-request-submission-section"}
             className={styles.section}>
            <InformationRequestAmendmentSummary requestId={request.id}
                                                accessLinkToken={accessLinkToken}
                                                refreshKey={workspace.responseETag}/>
            <InformationRequestCarryForwardList requestId={request.id}
                                                accessLinkToken={accessLinkToken}
                                                requirementLabels={requirementLabels}/>
            <InformationRequestSubmissionPanel requestId={request.id}
                                               requestState={request.state}
                                               responseETag={workspace.responseETag}
                                               accessLinkToken={accessLinkToken}
                                               requirementLabels={requirementLabels}
                                               reviewItems={reviewItems}
                                               onChanged={onChanged}/>
            <InformationRequestReviewResults requestId={request.id}
                                             accessLinkToken={accessLinkToken}
                                             refreshKey={workspace.responseETag}
                                             requirementLabels={requirementLabels}
                                             onChanged={onChanged}/>
            {message && (
                <MessageBar id={"information-request-supplement-message"}
                            intent={message.intent}>
                    <MessageBarBody>{message.text}</MessageBarBody>
                </MessageBar>
            )}
            {canRequestSupplement && (
                <div id={"information-request-follow-up-actions"}
                     className={styles.actions}>
                    <Button id={"information-request-request-supplement"}
                            appearance={"secondary"}
                            shape={"circular"}
                            disabled={busy}
                            onClick={() => setDialogOpen(true)}>
                        Request more information
                    </Button>
                </div>
            )}
            {dialogOpen && (
                <InformationRequestSupplementDialog busy={busy}
                                                    onConfirm={reason => void requestSupplement(reason)}
                                                    onDismiss={() => setDialogOpen(false)}/>
            )}
        </div>
    );
};

export default InformationRequestSubmissionSection;
