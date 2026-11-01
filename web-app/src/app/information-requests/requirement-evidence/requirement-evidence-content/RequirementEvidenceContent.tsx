import {useMemo, useState} from "react";
import {MessageBar, MessageBarBody} from "@fluentui/react-components";
import {InformationRequestEvidenceArtifactDto} from "../../../models/models.tsx";
import InformationRequestLoadError from "../../shared/load-error/InformationRequestLoadError.tsx";
import EvidenceArtifactList from "../evidence-artifact-list/EvidenceArtifactList.tsx";
import EvidenceDetailsDialog from "../evidence-details-dialog/EvidenceDetailsDialog.tsx";
import EvidenceKeptUploadList from "../evidence-kept-upload-list/EvidenceKeptUploadList.tsx";
import EvidenceUploadRow from "../evidence-upload-row/EvidenceUploadRow.tsx";
import EvidenceWithdrawDialog from "../evidence-withdraw-dialog/EvidenceWithdrawDialog.tsx";
import {capturedEvidenceDetails, detailsOfVersion, EvidenceDetails} from "../evidenceDetails.ts";
import RequirementEvidenceSummary from "../requirement-evidence-summary/RequirementEvidenceSummary.tsx";
import {RequirementEvidenceSettings} from "../RequirementEvidenceContext.ts";
import {RequirementEvidencePanelProps} from "../RequirementEvidenceTypes.ts";
import {useRequirementEvidence} from "../useRequirementEvidence.ts";
import {useRequirementEvidenceContentStyles} from "./RequirementEvidenceContentStyles.tsx";

interface Props extends RequirementEvidencePanelProps
{
    settings: RequirementEvidenceSettings;
}

interface ChosenFile {
    file: File;
    artifact?: InformationRequestEvidenceArtifactDto;
}

const RequirementEvidenceContent = ({
                                        requestId,
                                        requirementId,
                                        prompt,
                                        required,
                                        helpText,
                                        elementId,
                                        locked = false,
                                        evidencePolicy,
                                        settings
                                    }: Props) =>
{
    const styles = useRequirementEvidenceContentStyles();
    const {
        evidence,
        loadError,
        reload,
        busy,
        progress,
        message,
        kept,
        retry,
        discard,
        upload,
        replace,
        withdraw,
        open
    } =
        useRequirementEvidence(requestId, requirementId, settings.commands, settings.keptUploads);
    const [withdrawing, setWithdrawing] = useState<InformationRequestEvidenceArtifactDto | null>(null);
    const [chosen, setChosen] = useState<ChosenFile | null>(null);
    const captured = useMemo(() => capturedEvidenceDetails(evidencePolicy), [evidencePolicy]);
    const id = `information-request-evidence-${elementId}-PANEL`;

    const send = ({file, artifact}: ChosenFile, details: EvidenceDetails) => {
        void (artifact ? replace(artifact, file, details) : upload(file, details));
    };

    const choose = (file: File, artifact?: InformationRequestEvidenceArtifactDto) => {
        if (captured.length === 0) send({file, artifact}, {});
        else setChosen({file, artifact});
    };

    return (
        <div id={id}
             className={styles.panel}>
            <RequirementEvidenceSummary id={id}
                                        prompt={prompt}
                                        required={required}
                                        helpText={helpText}
                                        evaluation={evidence?.evaluation}/>
            <EvidenceArtifactList id={id}
                                  evidence={evidence}
                                  loading={!evidence && !loadError}
                                  busy={busy}
                                  locked={locked}
                                  uploadAvailable={settings.uploadAvailable}
                                  onReplace={(artifact, file) => choose(file, artifact)}
                                  onWithdraw={setWithdrawing}
                                  onOpen={(artifact, version, use) => void open(artifact, version, use)}/>
            {!locked && (
                <EvidenceUploadRow id={id}
                                   uploadAvailable={settings.uploadAvailable}
                                   malwareScanning={settings.malwareScanning}
                                   disabled={busy || !evidence}
                                   progress={progress}
                                   onFile={file => choose(file)}/>
            )}
            {message && (
                <MessageBar id={`${id}-message`}
                            intent="warning"
                            role="alert">
                    <MessageBarBody id={`${id}-message-body`}>{message}</MessageBarBody>
                </MessageBar>
            )}
            {loadError && (
                <InformationRequestLoadError id={`${id}-load-error`}
                                             message={loadError}
                                             onRetry={() => void reload()}/>
            )}
            <EvidenceKeptUploadList id={`${id}-kept`}
                                    kept={kept}
                                    busy={busy}
                                    canRetry={evidence !== null && !locked}
                                    onRetry={upload => void retry(upload)}
                                    onDiscard={discard}/>
            {chosen && (
                <EvidenceDetailsDialog id={`${id}-details-dialog`}
                                       fileName={chosen.file.name}
                                       actionLabel={chosen.artifact ? "Replace" : "Upload"}
                                       captured={captured}
                                       initial={detailsOfVersion(captured, chosen.artifact?.versions[chosen.artifact.versions.length - 1])}
                                       onConfirm={details => {
                                           send(chosen, details);
                                           setChosen(null);
                                       }}
                                       onDismiss={() => setChosen(null)}/>
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

export default RequirementEvidenceContent;
