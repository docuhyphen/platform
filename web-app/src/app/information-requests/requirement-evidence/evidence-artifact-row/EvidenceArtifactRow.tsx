import {Badge, Button, Text} from "@fluentui/react-components";
import {ArrowDownloadRegular, ArrowSyncRegular, DismissCircleRegular, EyeRegular} from "@fluentui/react-icons";
import {
    InformationRequestEvidenceArtifactDto,
    InformationRequestEvidenceCollectionState,
    InformationRequestEvidenceVersionDto,
} from "../../../models/models.tsx";
import {InformationRequestEvidenceContentUse} from "../../../../services/informationRequestEvidenceService.ts";
import EvidenceFileButton from "../evidence-file-button/EvidenceFileButton.tsx";
import {statedEvidenceDetails} from "../evidenceDetails.ts";
import {
    conformancePresentation,
    distinctFindings,
    findingKey,
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
    locked: boolean;
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

const EvidenceArtifactRow = ({id, artifact, busy, locked, uploadAvailable, onReplace, onWithdraw, onOpen}: Props) =>
{
    const styles = useEvidenceArtifactRowStyles();
    const latest = artifact.versions[artifact.versions.length - 1];
    if (!latest) return null;

    const active = artifact.collectionState === InformationRequestEvidenceCollectionState.ACTIVE;
    const changeable = active && artifact.changeableByCaller && !locked;
    const previewable = INLINE_PREVIEW_TYPES.has((latest.declaredMediaType ?? "").toLowerCase());
    const conformance = latest.conformance ? conformancePresentation[latest.conformance] : null;
    const findings = distinctFindings(latest.findings);
    const meta = [`Version ${latest.versionNumber}`, formatEvidenceSize(latest.contentLength), collectionLabels[artifact.collectionState]]
        .filter(Boolean)
        .join(", ");
    const details = statedEvidenceDetails(latest);

    return (
        <div id={id}
             className={styles.row}>
            <div id={`${id}-summary`}
                 className={styles.summary}>
                <div id={`${id}-identity`}
                     className={styles.identity}>
                    <Text id={`${id}-name`}
                          className={styles.fileName}>
                        {latest.declaredFileName ?? "Evidence"}
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
            {details.length > 0 && (
                <Text id={`${id}-details`}
                      size={200}
                      className={styles.meta}>
                    {details.join("; ")}
                </Text>
            )}
            {active && findings.length > 0 && (
                <ul id={`${id}-findings`}
                    className={styles.findings}>
                    {findings.map(finding => (
                        <li id={`${id}-finding-${findingKey(finding)}`}
                            key={findingKey(finding)}
                            className={finding.blocking ? styles.blocking : styles.advisory}>
                            {findingMessage(finding)}
                        </li>
                    ))}
                </ul>
            )}
            {active && !artifact.changeableByCaller && !artifact.createdByCaller && (
                <Text id={`${id}-provided-by-other`}
                      size={200}
                      className={styles.meta}>
                    Provided by another party. Only that party can replace or withdraw it.
                </Text>
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
                <Button id={`${id}-download`}
                        shape="circular"
                        size="small"
                        icon={<ArrowDownloadRegular/>}
                        disabled={busy}
                        onClick={() => onOpen(latest, "content")}>
                    Download
                </Button>
                {changeable && uploadAvailable && (
                    <EvidenceFileButton id={`${id}-replace`}
                                        label="Replace"
                                        size="small"
                                        icon={<ArrowSyncRegular/>}
                                        disabled={busy}
                                        onFile={onReplace}/>
                )}
                {changeable && (
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
