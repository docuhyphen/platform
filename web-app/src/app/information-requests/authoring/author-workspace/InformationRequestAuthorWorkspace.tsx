import {useState} from "react";
import {MessageBar, MessageBarBody, Spinner} from "@fluentui/react-components";
import {useParams} from "react-router-dom";
import {useAuth} from "../../../../context/AuthContext.tsx";
import {
    InformationRequestExecutionStandingKind,
    InformationRequestShareRoleKey,
    InformationRequestState,
} from "../../../models/models.tsx";
import ExecutionStandingNotice from "../../shared/execution-standing-notice/ExecutionStandingNotice.tsx";
import {mayChangeUnderStanding} from "../../shared/executionStandingText.ts";
import {requirementPromptsOf} from "../../shared/requirementPrompts.ts";
import AccessLinkDialog from "../access-link-dialog/AccessLinkDialog.tsx";
import AuthorWorkspaceHeader from "../author-workspace-header/AuthorWorkspaceHeader.tsx";
import DecisionMakerNotice from "../decision-maker-notice/DecisionMakerNotice.tsx";
import FollowUpPanel from "../follow-up-panel/FollowUpPanel.tsx";
import PartyPanel from "../party-panel/PartyPanel.tsx";
import RecipientPreviewDialog from "../recipient-preview-dialog/RecipientPreviewDialog.tsx";
import RequestClockPanel from "../request-clock-panel/RequestClockPanel.tsx";
import RequestLifecycleActions from "../request-lifecycle-actions/RequestLifecycleActions.tsx";
import RequestOutcomesPanel from "../request-outcomes-panel/RequestOutcomesPanel.tsx";
import {mayCorrectAsOwner} from "./ownerPrivacyAccess.ts";
import {useAuthorWorkspace} from "./useAuthorWorkspace.ts";
import {useInformationRequestAuthorWorkspaceStyles} from "./InformationRequestAuthorWorkspaceStyles.tsx";

const EDITABLE_STATES = new Set([InformationRequestState.DRAFT, InformationRequestState.ISSUED, InformationRequestState.IN_PROGRESS]);

const InformationRequestAuthorWorkspace = () =>
{
    const styles = useInformationRequestAuthorWorkspaceStyles();
    const {requestId = ""} = useParams<{requestId: string}>();
    const state = useAuthorWorkspace(requestId);
    const [previewing, setPreviewing] = useState(false);
    const workspace = state.workspace;
    const editable = Boolean(workspace && EDITABLE_STATES.has(workspace.request.state) && mayChangeUnderStanding(workspace.executionStanding));
    const hasDecisionMaker = state.parties.some(party => party.active && party.roleKey === InformationRequestShareRoleKey.DECISION_MAKER);
    const {currentSession, hasCapability} = useAuth();
    const subjectId = state.parties
        .find(party => party.active && party.roleKey === InformationRequestShareRoleKey.SUBJECT && party.subjectIdentityRefId)
        ?.subjectIdentityRefId;

    return (
        <section id={"information-request-author-page"}
                 aria-label={workspace ? `Manage ${workspace.title}` : "Manage Information Request"}
                 className={styles.page}>
            {workspace && (
                <AuthorWorkspaceHeader title={workspace.title}
                                       state={workspace.request.state}
                                       exchangeId={workspace.request.exchangeId}
                                       onPreview={() => setPreviewing(true)}/>
            )}
            {workspace && (
                <ExecutionStandingNotice idPrefix={"information-request-author"}
                                         standing={workspace.executionStanding}/>
            )}
            {state.error && (
                <MessageBar id={"information-request-author-error"}
                            intent={"error"}
                            role={"alert"}>
                    <MessageBarBody>{state.error}</MessageBarBody>
                </MessageBar>
            )}
            <div id={"information-request-author-status"}
                 role={"status"}
                 aria-live={"polite"}>
                {state.notice && (
                    <MessageBar id={"information-request-author-notice"}
                                intent={"info"}>
                        <MessageBarBody>{state.notice}</MessageBarBody>
                    </MessageBar>
                )}
            </div>
            {!workspace && !state.loadError && (
                <Spinner id={"information-request-author-loading"}
                         label={"Loading Information Request"}/>
            )}
            {workspace && (
                <>
                    {editable && !hasDecisionMaker && (
                        <DecisionMakerNotice busy={state.busy}
                                             onNameSelf={() => void state.nameSelfDecisionMaker()}/>
                    )}
                    <RequestLifecycleActions request={workspace.request}
                                             busy={state.busy}
                                             onIssue={() => void state.issue()}
                                             onCancel={reason => void state.cancel(reason)}
                                             onSupersede={(replacementId, reason) => void state.supersede(replacementId, reason)}/>
                    <div id={"information-request-author-columns"}
                         className={styles.columns}>
                        <PartyPanel parties={state.parties}
                                    links={state.links}
                                    busy={state.busy}
                                    editable={editable}
                                    onRefresh={() => void state.reload()}
                                    onAssign={request => void state.assignParty(request)}
                                    onAssignSubject={request => void state.assignSubject(request)}
                                    onIssueLink={party => void state.issueLink(party)}
                                    onResendLink={(party, link) => void state.resendLink(party, link)}
                                    onRevokeLink={(party, link) => void state.revokeLink(party, link)}
                                    onRemove={party => void state.revokeParty(party)}/>
                        <RequestClockPanel requestId={requestId}
                                           editable={editable}/>
                    </div>
                    {workspace.request.state !== InformationRequestState.DRAFT && (
                        <FollowUpPanel request={workspace.request}
                                       canCreate={workspace.executionStanding.kind === InformationRequestExecutionStandingKind.ACTIVE}
                                       onChanged={() => void state.reload()}/>
                    )}
                    {workspace.request.state !== InformationRequestState.DRAFT && (
                        <RequestOutcomesPanel requestId={requestId}
                                              state={workspace.request.state}
                                              prompts={requirementPromptsOf(workspace)}
                                              subjectId={subjectId}
                                              canCorrect={mayCorrectAsOwner(workspace.request, currentSession, hasCapability)}/>
                    )}
                </>
            )}
            {previewing && workspace && (
                <RecipientPreviewDialog sections={workspace.templateVersion.sections}
                                        onDismiss={() => setPreviewing(false)}/>
            )}
            {state.shownLink && (
                <AccessLinkDialog link={state.shownLink}
                                  onDismiss={state.dismissShownLink}/>
            )}
        </section>
    );
};

export default InformationRequestAuthorWorkspace;
