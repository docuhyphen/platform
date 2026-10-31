import {useState} from "react";
import {useLocation} from "react-router-dom";
import {Spinner} from "@fluentui/react-components";
import {InformationRequestState} from "../../../models/models.tsx";
import ExecutionStandingNotice from "../../shared/execution-standing-notice/ExecutionStandingNotice.tsx";
import {requirementPromptsByKey, requirementPromptsOf} from "../../shared/requirementPrompts.ts";
import {InformationRequestManageArrival} from "../../shared/informationRequestWorkspacePaths.ts";
import AccessLinkDialog from "../access-link-dialog/AccessLinkDialog.tsx";
import AuthorWorkspaceHeader from "../author-workspace-header/AuthorWorkspaceHeader.tsx";
import AuthorWorkspaceMessages from "../author-workspace-messages/AuthorWorkspaceMessages.tsx";
import CompletionGateControl from "../completion-gate-control/CompletionGateControl.tsx";
import DecisionMakerGuidance from "../decision-maker-notice/DecisionMakerGuidance.tsx";
import ExpectedDocumentsPanel from "../expected-documents-panel/ExpectedDocumentsPanel.tsx";
import FollowUpPanel from "../follow-up-panel/FollowUpPanel.tsx";
import PartyPanel from "../party-panel/PartyPanel.tsx";
import RecipientPreviewDialog from "../recipient-preview-dialog/RecipientPreviewDialog.tsx";
import RequestClockPanel from "../request-clock-panel/RequestClockPanel.tsx";
import RequestLifecycleActions from "../request-lifecycle-actions/RequestLifecycleActions.tsx";
import RequestOutcomesPanel from "../request-outcomes-panel/RequestOutcomesPanel.tsx";
import RequestReviewsPanel from "../request-reviews-panel/RequestReviewsPanel.tsx";
import SubmissionRecoveryPanel from "../submission-recovery-panel/SubmissionRecoveryPanel.tsx";
import {maySubmissionsBeRecovered} from "../submission-recovery-panel/submissionRecoveryAccess.ts";
import {useAuthorWorkspace} from "../author-workspace/useAuthorWorkspace.ts";
import {useAuthorWorkspaceContentStyles} from "./AuthorWorkspaceContentStyles.tsx";
import {useAuthorPermissions} from "./useAuthorPermissions.ts";
import {RequestReadRefreshProvider} from "../../shared/request-read-refresh/RequestReadRefreshProvider.tsx";

interface Props {
    requestId: string;
}

const AuthorWorkspaceContent = ({requestId}: Props) => {
    const styles = useAuthorWorkspaceContentStyles();
    const state = useAuthorWorkspace(requestId);
    const [previewing, setPreviewing] = useState(false);
    const arrival = useLocation().state as InformationRequestManageArrival | null;
    const workspace = state.workspace;
    const {permissions, subjects, canCorrect} = useAuthorPermissions(workspace, state.parties);

    return (
        <RequestReadRefreshProvider generation={state.readGeneration}>
            <section id={"information-request-author-page"}
                     aria-label={workspace ? `Manage ${workspace.title}` : "Manage Information Request"}
                     className={styles.page}>
                {workspace && (
                    <AuthorWorkspaceHeader title={workspace.title}
                                           state={workspace.request.state}
                                           exchangeId={workspace.request.exchangeId}
                                           canPreview={permissions.preview}
                                           onPreview={() => setPreviewing(true)}/>
                )}
                {workspace && (
                    <ExecutionStandingNotice idPrefix={"information-request-author"}
                                             standing={workspace.executionStanding}/>
                )}
                <AuthorWorkspaceMessages error={state.error}
                                         notice={state.notice}
                                         onRetry={state.loadError ? () => void state.reload() : undefined}/>
                {!workspace && !state.loadError && (
                    <Spinner id={"information-request-author-loading"}
                             label={"Loading Information Request"}/>
                )}
                {workspace && (
                    <>
                        <DecisionMakerGuidance state={workspace.request.state}
                                               parties={state.parties}
                                               canChangeParties={permissions.parties.change}
                                               callerMayIssue={workspace.authorActions?.issue === true}
                                               busy={state.busy}
                                               failure={arrival?.decisionMakerFailure ?? null}
                                               onNameSelf={() => void state.nameSelfDecisionMaker()}/>
                        <RequestLifecycleActions request={workspace.request}
                                                 permissions={permissions.lifecycle}
                                                 busy={state.busy}
                                                 error={state.error}
                                                 notice={state.notice}
                                                 onIssue={() => void state.issue()}
                                                 onCancel={state.cancel}
                                                 onSupersede={state.supersede}/>
                        <CompletionGateControl gatesExchangeClosure={workspace.request.gatesExchangeClosure}
                                               editable={permissions.completionGate}
                                               busy={state.busy}
                                               onChange={gates => void state.changeCompletionGate(gates)}/>
                        <div id={"information-request-author-columns"}
                             className={styles.columns}>
                            <PartyPanel requestId={requestId}
                                        owner={workspace.request}
                                        parties={state.parties}
                                        links={state.links}
                                        linksError={state.linksError}
                                        busy={state.busy}
                                        error={state.error}
                                        notice={state.notice}
                                        permissions={permissions.parties}
                                        onRefresh={() => void state.reload()}
                                        onAssign={state.assignParty}
                                        onAssignSubject={state.assignSubject}
                                        onIssueLink={party => void state.issueLink(party)}
                                        onResendLink={(party, link) => void state.resendLink(party, link)}
                                        onRevokeLink={(party, link) => void state.revokeLink(party, link)}
                                        onReassign={state.reassignParty}
                                        onRemove={party => void state.revokeParty(party)}/>
                            <RequestClockPanel requestId={requestId}
                                               owner={workspace.request}
                                               permissions={permissions.clocks}/>
                        </div>
                        <ExpectedDocumentsPanel placeholders={state.placeholders}
                                                error={state.placeholdersError}
                                                onRetry={() => void state.reload()}/>
                        {workspace.request.state !== InformationRequestState.DRAFT && (
                            <RequestReviewsPanel requestId={requestId}/>
                        )}
                        {maySubmissionsBeRecovered(workspace) && (
                            <SubmissionRecoveryPanel requestId={requestId}
                                                     responseETag={workspace.responseETag}
                                                     onChanged={() => void state.reload()}/>
                        )}
                        <FollowUpPanel request={workspace.request}
                                       sections={workspace.templateVersion.sections}
                                       permissions={permissions.followUps}
                                       onChanged={() => void state.reload()}/>
                        {workspace.request.state !== InformationRequestState.DRAFT && (
                            <RequestOutcomesPanel requestId={requestId}
                                                  permissions={permissions.outcomes}
                                                  prompts={requirementPromptsOf(workspace)}
                                                  promptsByKey={requirementPromptsByKey(workspace.templateVersion.sections)}
                                                  subjects={subjects}
                                                  canCorrect={canCorrect}/>
                        )}
                    </>
                )}
                {previewing && workspace && permissions.preview && (
                    <RecipientPreviewDialog requestId={requestId}
                                            parties={state.parties}
                                            onDismiss={() => setPreviewing(false)}/>
                )}
                {workspace && state.shownLink && (
                    <AccessLinkDialog link={state.shownLink}
                                      onDismiss={state.dismissShownLink}/>
                )}
            </section>
        </RequestReadRefreshProvider>
    );
};

export default AuthorWorkspaceContent;
