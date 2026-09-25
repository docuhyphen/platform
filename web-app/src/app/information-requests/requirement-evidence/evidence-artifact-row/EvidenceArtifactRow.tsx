import {Badge, Button, Text} from "@fluentui/react-components";
import {ArrowDownloadRegular, ArrowSyncRegular, DismissCircleRegular, EyeRegular} from "@fluentui/react-icons";
import {
    InformationRequestEvidenceArtifactDto,
    InformationRequestEvidenceCollectionState,
    InformationRequestEvidenceSourceKind,
    InformationRequestEvidenceVersionDto,
} from "../../../models/models.tsx";
import {InformationRequestEvidenceContentUse} from "../../../../services/informationRequestEvidenceService.ts";
import EvidenceFileButton from "../evidence-file-button/EvidenceFileButton.tsx";
import {
    conformancePresentation,
    findingMessage,
    formatEvidenceSize,
    INLINE_PREVIEW_TYPES,
} from "../requirementEvidenceLabels.ts";
import {useEvidenceArtifactRowStyles} from "./EvidenceArtifactRowStyles.tsx";

interface Props
{
    id: string;
    artifact: InformationRequestEvidenceArtifactDto;
    busy: boolean;
    uploadAvailable: boolean;
    onReplace: (file: File) => void;
    onWithdraw: () => void;
    onOpen: (version: InformationRequestEvidenceVersionDto, use: InformationRequestEvidenceContentUse) => void;
}

const collectionLabels: Record<InformationRequestEvidenceCollectionState, string | null> = {
    [InformationRequestEvidenceCollectionState.ACTIVE]: null,
    [InformationRequestEvidenceCollectionState.WITHDRAWN]: "Withdrawn",
    [InformationRequestEvidenceCollectionState.REMOVED]: "Removed",
};

const EvidenceArtifactRow = ({id, artifact, busy, uploadAvailable, onReplace, onWithdraw, onOpen}: Props) =>
{
    const styles = useEvidenceArtifactRowStyles();
    const latest = artifact.versions[artifact.versions.length - 1];
    if (!latest) return null;

    const active = artifact.collectionState === InformationRequestEvidenceCollectionState.ACTIVE;
    const fileBacked = latest.sourceKind === InformationRequestEvidenceSourceKind.DOCUMENT_VERSION;
    const previewable = fileBacked && INLINE_PREVIEW_TYPES.has((latest.declaredMediaType ?? "").toLowerCase());
    const conformance = latest.conformance ? conformancePresentation[latest.conformance] : null;
    const findings = latest.findings.filter((finding, index, all) =>
        all.findIndex(candidate => candidate.code === finding.code) === index);
    const meta = [`Version ${latest.versionNumber}`, formatEvidenceSize(latest.contentLength), collectionLabels[artifact.collectionState]]
        .filter(Boolean)
        .join(", ");

    return (
        <div id={id}
             className={styles.row}>
            <div id={`${id}-summary`}
                 className={styles.summary}>
                <div id={`${id}-identity`}
                     className={styles.identity}>
                    <Text id={`${id}-name`}
                          className={styles.fileName}>
                        {latest.declaredFileName ?? latest.externalReferenceValue ?? "Evidence"}
                    </Text>
                    <Text id={`${id}-meta`}
                          size={200}
                          className={styles.meta}>
                        {meta}
                    </Text>
                </div>
                {conformance && active && (
                    <Badge id={`${id}-conformance`}
                           appearance="tint"
                           color={conformance.color}>
                        {conformance.label}
                    </Badge>
                )}
            </div>
            {active && findings.length > 0 && (
                <ul id={`${id}-findings`}
                    className={styles.findings}>
                    {findings.map(finding => (
                        <li id={`${id}-finding-${finding.code.toLowerCase()}`}
                            key={finding.code}
                            className={finding.blocking ? styles.blocking : styles.advisory}>
                            {findingMessage(finding)}
                        </li>
                    ))}
                </ul>
            )}
            <div id={`${id}-actions`}
                 className={styles.actions}>
                {previewable && (
                    <Button id={`${id}-preview`}
                            shape="circular"
                            size="small"
                            icon={<EyeRegular/>}
                            disabled={busy}
                            onClick={() => onOpen(latest, "preview")}>
                        Preview
                    </Button>
                )}
                {fileBacked && (
                    <Button id={`${id}-download`}
                            shape="circular"
                            size="small"
                            icon={<ArrowDownloadRegular/>}
                            disabled={busy}
                            onClick={() => onOpen(latest, "content")}>
                        Download
                    </Button>
                )}
                {active && uploadAvailable && (
                    <EvidenceFileButton id={`${id}-replace`}
                                        label="Replace"
                                        size="small"
                                        icon={<ArrowSyncRegular/>}
                                        disabled={busy}
                                        onFile={onReplace}/>
                )}
                {active && (
                    <Button id={`${id}-withdraw`}
                            shape="circular"
                            size="small"
                            icon={<DismissCircleRegular/>}
                            disabled={busy}
                            onClick={onWithdraw}>
                        Withdraw
                    </Button>
                )}
            </div>
        </div>
    );
};

export default EvidenceArtifactRow;
