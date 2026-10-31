import {FluentProvider, Spinner, Text, Title2} from "@fluentui/react-components";
import {lightTheme} from "../../../context/theme.ts";
import {InformationRequestState} from "../../models/models.tsx";
import AccountLinkOffer from "./account-link-offer/AccountLinkOffer.tsx";
import {usePendingAccountLink} from "./account-link-offer/usePendingAccountLink.ts";
import ContactProofPanel from "./contact-proof-panel/ContactProofPanel.tsx";
import RespondentLinkNotice from "./respondent-link-notice/RespondentLinkNotice.tsx";
import RespondentLoadError from "./respondent-load-error/RespondentLoadError.tsx";
import RespondentWorkspaceBody from "./respondent-workspace-body/RespondentWorkspaceBody.tsx";
import {useInformationRequestRespondentWorkspaceStyles} from "./InformationRequestRespondentWorkspaceStyles.tsx";
import {
    InformationRequestRespondentAccessMode,
    useInformationRequestRespondentWorkspace,
} from "./useInformationRequestRespondentWorkspace.ts";

interface Props
{
    accessMode: InformationRequestRespondentAccessMode;
}

const FINISHED_STATES = new Set([
    InformationRequestState.CLOSED,
    InformationRequestState.CANCELLED,
    InformationRequestState.SUPERSEDED,
    InformationRequestState.EXPIRED,
]);

const InformationRequestRespondentWorkspace = ({accessMode}: Props) =>
{
    const styles = useInformationRequestRespondentWorkspaceStyles();
    const {
        requestId,
        workspace,
        requirements,
        accessLinkToken,
        sessionId,
        accessVerified,
        linkIncomplete,
        challengeSent,
        code,
        busy,
        error,
        keptUploads,
        commandKeys,
        setCode,
        issueChallenge,
        verifyCode,
        loadWorkspace,
        endAccessSession,
    } = useInformationRequestRespondentWorkspace(accessMode);
    const pendingLink = usePendingAccountLink(requestId, sessionId);
    const linkOffered = accessMode === "no-auth" && (
        Boolean(workspace && accessLinkToken && sessionId && !FINISHED_STATES.has(workspace.request.state))
        || Boolean(pendingLink.attempt && !sessionId)
    );

    const page = (
            <section id={"information-request-respondent-page"}
                     aria-labelledby={"information-request-respondent-title"}
                     className={styles.page}>
                <header id={"information-request-respondent-header"}
                        className={styles.header}>
                    <div id={"information-request-respondent-title-group"}
                         className={styles.titleGroup}>
                        <Title2 id={"information-request-respondent-title"}
                                as={"h1"}>
                            {workspace?.title ?? "Information Request"}
                        </Title2>
                        <Text id={"information-request-respondent-subtitle"}
                              className={styles.mutedText}>
                            Provide the requested information.
                        </Text>
                    </div>
                </header>
                <main id={"information-request-respondent-content"}
                      className={styles.content}>
                    {error && (
                        <RespondentLoadError message={error}
                                             onRetry={requestId && accessVerified && !workspace && !busy
                                                 ? () => void loadWorkspace()
                                                 : undefined}/>
                    )}
                    {!requestId && (
                        <RespondentLinkNotice id={"information-request-respondent-missing-link"}
                                              message={"This Information Request link is missing its request id."}/>
                    )}
                    {linkIncomplete && (
                        <RespondentLinkNotice id={"information-request-respondent-incomplete-link"}
                                              message={"This link is incomplete. Open the link you were sent again, in full."}/>
                    )}
                    {linkOffered && (
                        <AccountLinkOffer requestId={requestId}
                                          accessLinkToken={accessLinkToken ?? ""}
                                          sessionId={sessionId}
                                          respondentKey={workspace?.respondentKey ?? ""}
                                          pending={pendingLink}
                                          onAccessSessionEnded={endAccessSession}/>
                    )}
                    {requestId && accessMode === "no-auth" && !accessVerified && !linkIncomplete && (
                        <div id={"information-request-respondent-proof-shell"}
                             className={styles.centered}>
                            <ContactProofPanel code={code}
                                               challengeSent={challengeSent}
                                               busy={busy}
                                               onCodeChange={setCode}
                                               onIssueChallenge={issueChallenge}
                                               onVerify={verifyCode}/>
                        </div>
                    )}
                    {requestId && accessVerified && !workspace && busy && (
                        <div id={"information-request-respondent-loading"}
                             className={styles.centered}>
                            <Spinner id={"information-request-respondent-loading-spinner"}
                                     size={"medium"}
                                     label={"Loading Information Request"}/>
                        </div>
                    )}
                    {workspace && (
                        <RespondentWorkspaceBody key={`${workspace.request.id}|${workspace.respondentKey}`}
                                                 workspace={workspace}
                                                 requirements={requirements}
                                                 accessLinkToken={accessLinkToken}
                                                 keptUploads={keptUploads}
                                                 commandKeys={commandKeys}
                                                 onRefresh={loadWorkspace}
                                                 onAccessSessionEnded={endAccessSession}/>
                    )}
                </main>
            </section>
    );

    return accessMode === "no-auth"
        ? (
            <FluentProvider id={"information-request-respondent-theme-provider"}
                            theme={lightTheme}
                            className={styles.themeProvider}>
                {page}
            </FluentProvider>
        )
        : page;
};

export default InformationRequestRespondentWorkspace;
