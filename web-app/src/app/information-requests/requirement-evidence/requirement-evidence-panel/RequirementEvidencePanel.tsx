import {useState} from "react";
import {Badge, MessageBar, MessageBarBody, ProgressBar, Text} from "@fluentui/react-components";
import {ArrowUploadRegular} from "@fluentui/react-icons";
import {InformationRequestEvidenceArtifactDto} from "../../../models/models.tsx";
import EvidenceArtifactRow from "../evidence-artifact-row/EvidenceArtifactRow.tsx";
import EvidenceFileButton from "../evidence-file-button/EvidenceFileButton.tsx";
import EvidenceWithdrawDialog from "../evidence-withdraw-dialog/EvidenceWithdrawDialog.tsx";
import {RequirementEvidenceSettings, useRequirementEvidenceSettings} from "../RequirementEvidenceContext.ts";
import {findingMessage, requirementStatePresentation} from "../requirementEvidenceLabels.ts";
import {useRequirementEvidence} from "../useRequirementEvidence.ts";
import {useRequirementEvidencePanelStyles} from "./RequirementEvidencePanelStyles.tsx";

interface Props
{
    requestId: string;
    requirementId: string;
    prompt: string;
    elementId: string;
}

const RequirementEvidenceContent = ({requestId, requirementId, prompt, elementId, settings}: Props & {
    settings: RequirementEvidenceSettings;
}) =>
{
    const styles = useRequirementEvidencePanelStyles();
    const {evidence, busy, progress, message, upload, replace, withdraw, open} =
        useRequirementEvidence(requestId, requirementId, settings.commands);
    const [withdrawing, setWithdrawing] = useState<InformationRequestEvidenceArtifactDto | null>(null);
    const id = `information-request-evidence-${elementId}`;
    const evaluation = evidence?.evaluation;
    const state = evaluation ? requirementStatePresentation[evaluation.state] : null;

    return (
        <div id={id}
             className={styles.panel}>
            <div id={`${id}-header`}
                 className={styles.header}>
                <Text id={`${id}-prompt`}
                      className={styles.prompt}>
                    {prompt}
                </Text>
                {state && (
                    <Badge id={`${id}-state`}
                           appearance="filled"
                           color={state.color}>
                        {state.label}
                    </Badge>
                )}
            </div>
            {evaluation?.satisfiedBySubstitute && (
                <Text id={`${id}-substitute`}
                      className={styles.note}>
                    An alternative Requirement provides this evidence.
                </Text>
            )}
            {evaluation && evaluation.findings.length > 0 && (
                <ul id={`${id}-findings`}
                    className={styles.findings}>
                    {evaluation.findings.map(finding => (
                        <li id={`${id}-finding-${finding.code.toLowerCase()}`}
                            key={finding.code}>
                            {findingMessage(finding)}
                        </li>
                    ))}
                </ul>
            )}
            <div id={`${id}-artifacts`}
                 className={styles.artifacts}>
                {evidence && evidence.artifacts.length === 0 && (
                    <Text id={`${id}-empty`}
                          className={styles.note}>
                        No files yet.
                    </Text>
                )}
                {evidence?.artifacts.map(artifact => (
                    <EvidenceArtifactRow id={`${id}-artifact-${artifact.artifactKey}`}
                                         key={artifact.id}
                                         artifact={artifact}
                                         busy={busy}
                                         uploadAvailable={settings.uploadAvailable}
                                         onReplace={file => void replace(artifact, file)}
                                         onWithdraw={() => setWithdrawing(artifact)}
                                         onOpen={(version, use) => void open(artifact, version, use)}/>
                ))}
            </div>
            <div id={`${id}-upload`}
                 className={styles.uploadRow}>
                {settings.uploadAvailable ? (
                    <EvidenceFileButton id={`${id}-upload-button`}
                                        label="Upload file"
                                        appearance="primary"
                                        icon={<ArrowUploadRegular/>}
                                        disabled={busy || !evidence}
                                        onFile={file => void upload(file)}/>
                ) : (
                    <Text id={`${id}-upload-unavailable`}
                          className={styles.note}>
                        Evidence upload is not available right now.
                    </Text>
                )}
                {progress !== null && (
                    <ProgressBar id={`${id}-progress`}
                                 className={styles.progress}
                                 value={progress / 100}/>
                )}
                {!settings.malwareScanning && (
                    <Text id={`${id}-scanning-note`}
                          size={200}
                          className={styles.note}>
                        Files are not scanned for malware.
                    </Text>
                )}
            </div>
            {message && (
                <MessageBar id={`${id}-message`}
                            intent="warning">
                    <MessageBarBody>{message}</MessageBarBody>
                </MessageBar>
            )}
            {withdrawing && (
                <EvidenceWithdrawDialog id={`${id}-withdraw-dialog`}
                                        fileName={withdrawing.versions[withdrawing.versions.length - 1]?.declaredFileName ?? "This file"}
                                        busy={busy}
                                        onConfirm={reason => void withdraw(withdrawing, reason).then(() => setWithdrawing(null))}
                                        onDismiss={() => setWithdrawing(null)}/>
            )}
        </div>
    );
};

const RequirementEvidencePanel = (props: Props) =>
{
    const settings = useRequirementEvidenceSettings();
    return settings ? <RequirementEvidenceContent {...props} settings={settings}/> : null;
};

export default RequirementEvidencePanel;
