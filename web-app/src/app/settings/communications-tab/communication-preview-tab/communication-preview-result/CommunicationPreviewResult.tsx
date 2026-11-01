import {Text} from '@fluentui/react-components';
import {RenderedCommunication} from '../../../../models/models';
import {useCommunicationPreviewResultStyles} from './CommunicationPreviewResultStyles';

interface Props
{
    result: RenderedCommunication;
}

const CommunicationPreviewResult = ({result}: Props) =>
{
    const styles = useCommunicationPreviewResultStyles();

    return (
        <div id={"communication-editor-dialog-div-6"}
             className={styles.previewResultsContainer}>
            <div id={"communication-editor-dialog-div-7"}
                 className={styles.previewBox}>
                <Text
                    id={"communication-editor-dialog-text-4"}
                    size={200}
                    weight="semibold"
                    className={styles.previewSubjectLabel}
                >
                    Subject
                </Text>
                <Text id={"communication-editor-dialog-text-5"}
                      size={400}
                      weight="semibold">{result.subject}</Text>
            </div>
            <div id={"communication-editor-dialog-div-8"}
                 className={styles.previewBox}>
                <Text
                    id={"communication-editor-dialog-text-6"}
                    size={200}
                    weight="semibold"
                    className={styles.previewBodyLabel}
                >
                    Body (Markdown)
                </Text>
                <pre id={"communication-editor-dialog-pre-1"}
                     className={styles.previewPre}>
                    {result.body}
                </pre>
            </div>
        </div>
    );
};

export default CommunicationPreviewResult;
