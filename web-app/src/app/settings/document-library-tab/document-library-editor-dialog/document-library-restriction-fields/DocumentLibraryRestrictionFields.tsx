import {Checkbox, Dropdown, Field, Option, OptionGroup, Switch} from '@fluentui/react-components';
import {useExchangeInitiationStyles} from '../../../../exchange-initiation/ExchangeInitiationStyles.tsx';
import {documentLibraryRestrictedTypeGroups} from './documentLibraryRestrictedTypes.ts';

interface DocumentLibraryRestrictionFieldsProps
{
    restrictType: boolean;
    restrictedType: string | undefined;
    required: boolean;
    onRestrictTypeChange: (checked: boolean) => void;
    onRestrictedTypeChange: (value: string | undefined) => void;
    onRequiredChange: (checked: boolean) => void;
}

const DocumentLibraryRestrictionFields = ({
    restrictType,
    restrictedType,
    required,
    onRestrictTypeChange,
    onRestrictedTypeChange,
    onRequiredChange,
}: DocumentLibraryRestrictionFieldsProps) =>
{
    const exchangeStyles = useExchangeInitiationStyles();

    return (
        <div id={"document-library-editor-dialog-div-1"}
             className={exchangeStyles.exchangeDocumentsRestriction}>
            <div id={"document-library-editor-dialog-div-2"}
                 className={exchangeStyles.exchangeDocumentsRestrictionField}>
                <Field id={"document-library-editor-dialog-field-3"}
                       label="">
                    <Switch id="doc-lib-restrict-type-switch"
                            checked={restrictType}
                            label="Restrict upload type"
                            onChange={(_, d) => onRestrictTypeChange(d.checked)}/>
                </Field>
                <Dropdown id="doc-lib-restricted-type-dropdown"
                          className={exchangeStyles.exchangeDocumentsDropdown}
                          disabled={!restrictType}
                          value={restrictedType ?? ''}
                          selectedOptions={restrictedType ? [restrictedType] : []}
                          onOptionSelect={(_, d) => onRestrictedTypeChange(d.optionValue)}
                          placeholder="Select allowed type...">
                    {documentLibraryRestrictedTypeGroups.map(group => (
                        <OptionGroup id={`document-library-editor-dialog-option-group-${group.ordinal}`}
                                     key={group.label}
                                     label={group.label}>
                            {group.options.map(option => (
                                <Option id={`document-library-editor-dialog-option-${option.ordinal}`}
                                        key={option.value}
                                        value={option.value}>{option.value}</Option>
                            ))}
                        </OptionGroup>
                    ))}
                </Dropdown>
            </div>
            <Checkbox id="doc-lib-required-checkbox"
                      checked={required}
                      onChange={(_, d) => onRequiredChange(!!d.checked)}
                      label="Required"/>
        </div>
    );
};

export default DocumentLibraryRestrictionFields;
