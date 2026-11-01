import {Field, Input} from '@fluentui/react-components';
import VariableTokenInput from '../../../../../components/variable-token-input/VariableTokenInput.tsx';
import {AvailableVariablesDto} from '../../../../models/models.tsx';

interface DocumentLibraryTitleFieldProps
{
    title: string;
    onChange: (value: string) => void;
    availableVariables: AvailableVariablesDto | null;
    error: string | null;
}

const DocumentLibraryTitleField = ({title, onChange, availableVariables, error}: DocumentLibraryTitleFieldProps) =>
{
    const titleMissing = !title.trim();

    return (
        <Field id={"document-library-editor-dialog-field-1"}
               label="Title"
               required
               validationState={error && titleMissing ? 'error' : 'none'}
               validationMessage={error && titleMissing ? error : undefined}>
            {availableVariables !== null ? (
                <VariableTokenInput value={title}
                                    onChange={onChange}
                                    availableVariables={availableVariables}
                                    placeholder="Document title"/>
            ) : (
                <Input id="doc-editor-title"
                       value={title}
                       onChange={(_, d) => onChange(d.value)}
                       placeholder="Document title"/>
            )}
        </Field>
    );
};

export default DocumentLibraryTitleField;
