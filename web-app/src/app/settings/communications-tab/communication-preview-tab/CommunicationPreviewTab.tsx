import {Button, Field, Spinner, Text, Textarea} from '@fluentui/react-components';
import {RenderedCommunication} from '../../../models/models';
import CommunicationPreviewResult from './communication-preview-result/CommunicationPreviewResult';
import {useCommunicationPreviewTabStyles} from './CommunicationPreviewTabStyles';

interface Props
{
    previewVars: string;
    onPreviewVarsChange: (value: string) => void;
    previewing: boolean;
    previewError: string | null;
    previewResult: RenderedCommunication | null;
    onRender: () => void;
}

const CommunicationPreviewTab = (props: Props) =>
{
    const {previewVars, onPreviewVarsChange, previewing, previewError, previewResult, onRender} = props;
    const styles = useCommunicationPreviewTabStyles();

    return (
        <div id={"communication-editor-dialog-div-5"}
             className={styles.tabContent}>
            <Field
                id={"communication-editor-dialog-field-6"}
                label="Sample variables (JSON)"
                hint='Override tokens for preview, e.g. {"exchangeName": "Contract Review"}'
            >
                <Textarea
                    id={"textarea-comm-preview-vars"}
                    value={previewVars}
                    onChange={(_, d) => onPreviewVarsChange(d.value)}
                    rows={3}
                    placeholder='{"exchangeName": "My Exchange", "initiatorName": "Jane Smith"}'
                />
            </Field>
            <Button
                id={"button-comm-render-preview"}
                appearance="secondary"
                shape={"circular"}
                onClick={onRender}
                disabled={previewing}
                className={styles.renderPreviewButton}
            >
                {previewing ? <><Spinner id={"communication-editor-dialog-spinner-1"}
                                         size="tiny"/> Rendering…</> : 'Render Preview'}
            </Button>
            {previewError && (
                <Text id={"communication-editor-dialog-text-3"}
                      className={styles.previewErrorText}>{previewError}</Text>
            )}
            {previewResult && (
                <CommunicationPreviewResult result={previewResult}/>
            )}
        </div>
    );
};

export default CommunicationPreviewTab;
