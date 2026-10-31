import {Spinner, Text} from "@fluentui/react-components";
import {
    InformationRequestEvidenceArtifactDto,
    InformationRequestEvidenceListDto,
    InformationRequestEvidenceVersionDto,
} from "../../../models/models.tsx";
import {InformationRequestEvidenceContentUse} from "../../../../services/informationRequestEvidenceService.ts";
import EvidenceArtifactRow from "../evidence-artifact-row/EvidenceArtifactRow.tsx";
import {useEvidenceArtifactListStyles} from "./EvidenceArtifactListStyles.tsx";

interface Props {
    id: string;
    evidence: InformationRequestEvidenceListDto | null;
    loading: boolean;
    busy: boolean;
    locked: boolean;
    uploadAvailable: boolean;
    onReplace: (artifact: InformationRequestEvidenceArtifactDto, file: File) => void;
    onWithdraw: (artifact: InformationRequestEvidenceArtifactDto) => void;
    onOpen: (
        artifact: InformationRequestEvidenceArtifactDto,
        version: InformationRequestEvidenceVersionDto,
        use: InformationRequestEvidenceContentUse,
    ) => void;
}

const EvidenceArtifactList = ({
                                  id,
                                  evidence,
                                  loading,
                                  busy,
                                  locked,
                                  uploadAvailable,
                                  onReplace,
                                  onWithdraw,
                                  onOpen
                              }: Props) => {
    const styles = useEvidenceArtifactListStyles();

    return (
        <div id={`${id}-artifacts`}
             className={styles.artifacts}>
            {loading && (
                <Spinner id={`${id}-loading`}
                         size={"tiny"}
                         label={"Loading files"}/>
            )}
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
                                     locked={locked}
                                     uploadAvailable={uploadAvailable}
                                     onReplace={file => onReplace(artifact, file)}
                                     onWithdraw={() => onWithdraw(artifact)}
                                     onOpen={(version, use) => onOpen(artifact, version, use)}/>
            ))}
        </div>
    );
};

export default EvidenceArtifactList;
