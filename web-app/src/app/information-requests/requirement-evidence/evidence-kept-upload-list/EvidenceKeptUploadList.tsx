import EvidenceKeptUpload from "../evidence-kept-upload/EvidenceKeptUpload.tsx";
import {KeptUpload} from "../keptEvidenceUploads.ts";
import {useEvidenceKeptUploadListStyles} from "./EvidenceKeptUploadListStyles.tsx";

interface Props
{
    id: string;
    kept: KeptUpload[];
    busy: boolean;
    canRetry: boolean;
    onRetry: (upload: KeptUpload) => void;
    onDiscard: (upload: KeptUpload) => void;
}

const EvidenceKeptUploadList = ({id, kept, busy, canRetry, onRetry, onDiscard}: Props) =>
{
    const styles = useEvidenceKeptUploadListStyles();

    if (kept.length === 0) return null;

    return (
        <div id={id}
             className={styles.list}>
            {kept.map((upload, index) => (
                <EvidenceKeptUpload id={`${id}-${index + 1}`}
                                    key={upload.idempotencyKey}
                                    fileName={upload.file.name}
                                    busy={busy}
                                    canRetry={canRetry}
                                    onRetry={() => onRetry(upload)}
                                    onDiscard={() => onDiscard(upload)}/>
            ))}
        </div>
    );
};

export default EvidenceKeptUploadList;
