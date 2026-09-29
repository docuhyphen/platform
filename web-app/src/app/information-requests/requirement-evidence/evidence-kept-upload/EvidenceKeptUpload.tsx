import {Button, Text} from "@fluentui/react-components";
import {ArrowClockwiseRegular} from "@fluentui/react-icons";
import {useEvidenceKeptUploadStyles} from "./EvidenceKeptUploadStyles.tsx";

interface Props
{
    id: string;
    fileName: string;
    busy: boolean;
    onRetry: () => void;
    onDiscard: () => void;
}

const EvidenceKeptUpload = ({id, fileName, busy, onRetry, onDiscard}: Props) =>
{
    const styles = useEvidenceKeptUploadStyles();

    return (
        <div id={id}
             role={"group"}
             aria-label={`${fileName} was not sent`}
             className={styles.kept}>
            <Text id={`${id}-text`}
                  className={styles.text}>
                {`${fileName} was not sent. It is kept here so you can try again.`}
            </Text>
            <Button id={`${id}-retry`}
                    appearance={"primary"}
                    shape={"circular"}
                    size={"small"}
                    icon={<ArrowClockwiseRegular/>}
                    aria-label={`Retry uploading ${fileName}`}
                    disabled={busy}
                    onClick={onRetry}>
                Retry
            </Button>
            <Button id={`${id}-discard`}
                    appearance={"subtle"}
                    shape={"circular"}
                    size={"small"}
                    aria-label={`Discard ${fileName}`}
                    disabled={busy}
                    onClick={onDiscard}>
                Discard
            </Button>
        </div>
    );
};

export default EvidenceKeptUpload;
