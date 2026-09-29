import {FluentProvider, MessageBar, MessageBarBody, Spinner, Text, Title2} from "@fluentui/react-components";
import {lightTheme} from "../../../context/theme.ts";
import ContactProofPanel from "./ContactProofPanel.tsx";
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

const InformationRequestRespondentWorkspace = ({accessMode}: Props) =>
{
    const styles = useInformationRequestRespondentWorkspaceStyles();
    const {
        requestId,
        workspace,
        requirements,
        accessLinkToken,
        accessVerified,
        challengeSent,
        code,
        busy,
        error,
        setCode,
        issueChallenge,
        verifyCode,
        loadWorkspace,
    } = useInformationRequestRespondentWorkspace(accessMode);

    return (
        <FluentProvider id={"information-request-respondent-theme-provider"}
                        theme={lightTheme}
                        className={styles.themeProvider}>
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
                        <MessageBar id={"information-request-respondent-error"}
                                    intent={"error"}>
                            <MessageBarBody>{error}</MessageBarBody>
                        </MessageBar>
                    )}
                    {!requestId && (
                        <div id={"information-request-respondent-missing-link"}
                             className={styles.centered}>
                            <Text className={styles.error}>
                                This Information Request link is missing its request id.
                            </Text>
                        </div>
                    )}
                    {requestId && accessMode === "no-auth" && !accessVerified && (
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
                            <Spinner size={"medium"}
                                     label={"Loading Information Request"}/>
                        </div>
                    )}
                    {workspace && (
                        <RespondentWorkspaceBody workspace={workspace}
                                                 requirements={requirements}
                                                 accessLinkToken={accessLinkToken}
                                                 onRefresh={() => void loadWorkspace()}/>
                    )}
                </main>
            </section>
        </FluentProvider>
    );
};

export default InformationRequestRespondentWorkspace;
