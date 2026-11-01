import {BlueprintEditorForm} from '../../useBlueprintEditorForm.ts';
import BlueprintVariableField from '../../blueprint-variable-field/BlueprintVariableField.tsx';

interface BlueprintExchangeDetailsSectionProps
{
    form: BlueprintEditorForm;
}

const BlueprintExchangeDetailsSection = ({form}: BlueprintExchangeDetailsSectionProps) =>
{
    const {values, updateConfig, availableVariables} = form;

    return (
        <>
            <BlueprintVariableField fieldId={"blueprint-editor-exchange-name-field"}
                                    label="Exchange Name"
                                    hint="Pre-fills the Exchange name on initiation"
                                    value={values.config.name ?? ''}
                                    onChange={name => updateConfig({name})}
                                    availableVariables={availableVariables}
                                    variablePlaceholder="Exchange name, type {{ to insert a variable"
                                    plainPlaceholder="Exchange name"
                                    plainInputId={"blueprint-editor-input"}/>
            <BlueprintVariableField fieldId={"blueprint-editor-exchange-description-field"}
                                    label="Exchange Description"
                                    hint="Pre-fills the Exchange description on initiation"
                                    value={values.config.description ?? ''}
                                    onChange={description => updateConfig({description})}
                                    availableVariables={availableVariables}
                                    variablePlaceholder="Exchange description, type {{ to insert a variable"
                                    plainPlaceholder="Exchange description"
                                    plainInputId={"blueprint-editor-textarea"}
                                    rows={3}/>
            <BlueprintVariableField fieldId={"blueprint-editor-initial-share-message-field"}
                                    label="Initial Share Message"
                                    hint="Message shown to recipients when they open the Exchange"
                                    value={values.config.initialShareMessage ?? ''}
                                    onChange={initialShareMessage => updateConfig({initialShareMessage})}
                                    availableVariables={availableVariables}
                                    variablePlaceholder="Message shown to recipient when they open the Exchange"
                                    plainPlaceholder="Message shown to recipient"
                                    plainInputId={"blueprint-editor-textarea-2"}
                                    rows={2}/>
        </>
    );
};

export default BlueprintExchangeDetailsSection;
