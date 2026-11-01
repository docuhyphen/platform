import {Field, Input} from '@fluentui/react-components';
import {AvailableVariablesDto} from '../../../models/models';
import {CommunicationEditorValues} from '../communicationEditorTypes';
import CommunicationVariableField from './communication-variable-field/CommunicationVariableField';
import CommunicationTokenHints from './communication-token-hints/CommunicationTokenHints';
import CommunicationTagEditor from './communication-tag-editor/CommunicationTagEditor';
import {useCommunicationDetailsTabStyles} from './CommunicationDetailsTabStyles';

interface Props
{
    values: CommunicationEditorValues;
    onValueChange: <K extends keyof CommunicationEditorValues>(key: K, value: CommunicationEditorValues[K]) => void;
    availableVariables: AvailableVariablesDto | null;
    variableHints: string[];
    onInsertHint: (hint: string) => void;
    tagInput: string;
    onTagInputChange: (value: string) => void;
    onAddTag: () => void;
    onRemoveTag: (tag: string) => void;
}

const CommunicationDetailsTab = (props: Props) =>
{
    const {values, onValueChange, availableVariables, variableHints, onInsertHint} = props;
    const styles = useCommunicationDetailsTabStyles();

    return (
        <div id={"communication-editor-dialog-div-1"}
             className={styles.tabContent}>
            <Field id={"communication-editor-dialog-field-1"}
                   label="Name"
                   required>
                <Input
                    id={"input-comm-name"}
                    value={values.name}
                    onChange={(_, d) => onValueChange('name', d.value)}
                    placeholder="Communication name"
                />
            </Field>
            <Field id={"communication-editor-dialog-field-2"}
                   label="Summary">
                <Input
                    id={"input-comm-summary"}
                    value={values.summary}
                    onChange={(_, d) => onValueChange('summary', d.value)}
                    placeholder="Short description"
                />
            </Field>
            <CommunicationVariableField
                fieldId={"communication-editor-dialog-field-3"}
                label="Subject"
                hint="Supports {{variable}} tokens"
                value={values.subject}
                onChange={value => onValueChange('subject', value)}
                availableVariables={availableVariables}
                multiline={false}
                tokenPlaceholder="Email subject line, type {{ to insert a variable"
                fallbackId={"input-comm-subject"}
                fallbackPlaceholder="Email subject line"
            />
            <CommunicationVariableField
                fieldId={"communication-editor-dialog-field-4"}
                label="Body"
                hint="Supports Markdown and {{variable}} tokens. Used for email body and in-app notifications."
                value={values.body}
                onChange={value => onValueChange('body', value)}
                availableVariables={availableVariables}
                multiline={true}
                tokenPlaceholder="Message body in Markdown. Type {{ to insert a variable."
                fallbackId={"textarea-comm-body"}
                fallbackPlaceholder="Message body in Markdown"
            />
            {variableHints.length > 0 && (
                <CommunicationTokenHints
                    hints={variableHints}
                    onInsert={onInsertHint}
                />
            )}
            <CommunicationTagEditor
                tags={values.tags}
                tagInput={props.tagInput}
                onTagInputChange={props.onTagInputChange}
                onAddTag={props.onAddTag}
                onRemoveTag={props.onRemoveTag}
            />
        </div>
    );
};

export default CommunicationDetailsTab;
