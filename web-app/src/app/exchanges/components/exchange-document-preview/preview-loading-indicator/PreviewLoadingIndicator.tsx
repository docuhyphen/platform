import {Spinner, Text} from "@fluentui/react-components";
import {usePreviewLoadingIndicatorStyles} from "./PreviewLoadingIndicatorStyles.tsx";

interface PreviewLoadingIndicatorProps
{
    containerId: string;
    spinnerId: string;
    textId: string;
}

const PreviewLoadingIndicator = ({containerId, spinnerId, textId}: PreviewLoadingIndicatorProps) =>
{
    const styles = usePreviewLoadingIndicatorStyles();

    return (
        <div id={containerId}
             className={styles.pdfLoadingContainer}>
            <Spinner id={spinnerId}
                     size="small"/>
            <Text id={textId}
                  size={300}
                  className={styles.pdfLoadingText}>Loading Document</Text>
        </div>
    );
};

export default PreviewLoadingIndicator;
