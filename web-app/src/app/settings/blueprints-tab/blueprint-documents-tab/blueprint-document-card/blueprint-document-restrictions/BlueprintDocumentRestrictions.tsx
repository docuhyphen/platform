import {Checkbox, Dropdown, Field, Option, OptionGroup, Switch} from '@fluentui/react-components';
import {BlueprintDocumentConfig, DocumentType, ImageType} from '../../../../../models/models.tsx';
import {useExchangeInitiationStyles} from '../../../../../exchange-initiation/ExchangeInitiationStyles.tsx';

interface BlueprintDocumentRestrictionsProps
{
    index: number;
    document: BlueprintDocumentConfig;
    onChange: (patch: Partial<BlueprintDocumentConfig>) => void;
}

const BlueprintDocumentRestrictions = ({index: i, document: doc, onChange}: BlueprintDocumentRestrictionsProps) =>
{
    const styles = useExchangeInitiationStyles();
    const linked = !!doc.libraryDocumentId;

    return (
        <div id={`blueprint-editor-exchange-documents-restriction-${i}`}
             className={styles.exchangeDocumentsRestriction}>
            <div id={`blueprint-editor-exchange-documents-restriction-field-${i}`}
                 className={styles.exchangeDocumentsRestrictionField}>
                <Field
                    id={`blueprint-editor-exchange-documents-restriction-field-field-${i}`}
                    label="">
                    <Switch
                        id={`bp-doc-restrict-type-${i}`}
                        label="Restrict upload type"
                        checked={doc.restrictType ?? false}
                        disabled={linked}
                        onChange={(_, d) => onChange({
                            restrictType: d.checked,
                            restrictedType: d.checked ? doc.restrictedType : undefined
                        })}
                    />
                </Field>
                <Dropdown
                    id={`bp-doc-restricted-type-${i}`}
                    className={styles.exchangeDocumentsDropdown}
                    disabled={!doc.restrictType || linked}
                    appearance="underline"
                    value={doc.restrictedType ?? ''}
                    size="small"
                    placeholder="Select allowed upload type"
                    onOptionSelect={(_, d) => onChange({restrictedType: d.optionValue as string})}
                >
                    <OptionGroup id={`blueprint-editor-documents-option-group-${i}`}
                                 label="Documents">
                        {Object.values(DocumentType).map(opt => (
                            <Option
                                id={`blueprint-editor-opt-option-${i}-${opt}`}
                                key={opt}
                                value={opt}
                            >
                                {opt}
                            </Option>
                        ))}
                    </OptionGroup>
                    <OptionGroup id={`blueprint-editor-images-option-group-${i}`}
                                 label="Images">
                        {Object.values(ImageType).map(opt => (
                            <Option
                                id={`blueprint-editor-opt-option-2-${i}-${opt}`}
                                key={opt}
                                value={opt}
                            >
                                {opt}
                            </Option>
                        ))}
                    </OptionGroup>
                </Dropdown>
            </div>
            <Checkbox
                id={`bp-doc-required-${i}`}
                label="Required"
                checked={doc.required ?? false}
                disabled={linked}
                onChange={(_, d) => onChange({required: !!d.checked})}
            />
        </div>
    );
};

export default BlueprintDocumentRestrictions;
