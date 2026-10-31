import {ProgressBar, Text} from "@fluentui/react-components";
import {ArrowUploadRegular} from "@fluentui/react-icons";
import EvidenceFileButton from "../evidence-file-button/EvidenceFileButton.tsx";
import {useEvidenceUploadRowStyles} from "./EvidenceUploadRowStyles.tsx";

interface Props {
    id: string;
    uploadAvailable: boolean;
    malwareScanning: boolean;
    disabled: boolean;
    progress: number | null;
    onFile: (file: File) => void;
}

const EvidenceUploadRow = ({id, uploadAvailable, malwareScanning, disabled, progress, onFile}: Props) => {
    const styles = useEvidenceUploadRowStyles();

    return (
        <div id={`${id}-upload`}
             className={styles.uploadRow}>
            {uploadAvailable ? (
                <EvidenceFileButton id={`${id}-upload-button`}
                                    label="Upload file"
                                    appearance="primary"
                                    icon={<ArrowUploadRegular/>}
                                    disabled={disabled}
                                    onFile={onFile}/>
            ) : (
                <Text id={`${id}-upload-unavailable`}
                      className={styles.note}>
                    Evidence upload is not available right now.
                </Text>
            )}
            {progress !== null && (
                <ProgressBar id={`${id}-progress`}
                             className={styles.progress}
                             aria-label={`Uploading, ${Math.round(progress)} percent sent`}
                             value={progress / 100}/>
            )}
            {!malwareScanning && (
                <Text id={`${id}-scanning-note`}
                      size={200}
                      className={styles.note}>
                    Files are not scanned for malware.
                </Text>
            )}
        </div>
    );
};

export default EvidenceUploadRow;
