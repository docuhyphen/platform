import React from 'react';
import {Dropdown, Option, OptionGroup} from "@fluentui/react-components";
import {DocumentType, ImageType} from "../../../../models/models.tsx";
import {useExchangeInitiationStyles} from "../../../ExchangeInitiationStyles.tsx";

interface DocumentTypeDropdownProps
{
    index: number;
    value: string | undefined;
    disabled: boolean;
    onChange: (newType: DocumentType | ImageType) => void;
}

const DocumentTypeDropdown: React.FC<DocumentTypeDropdownProps> = ({index, value, disabled, onChange}) =>
{
    const styles = useExchangeInitiationStyles();

    return (
        <Dropdown
            id={`doc-card-type-dropdown-${index}`}
            className={styles.exchangeDocumentsDropdown}
            disabled={disabled}
            appearance="underline"
            value={value ?? ''}
            size="small"
            placeholder="Select allowed upload type"
            onOptionSelect={(_e, data) =>
            {
                const selectedType = data.optionValue as DocumentType | ImageType | undefined;
                if (selectedType)
                {
                    onChange(selectedType);
                }
            }}
        >
            <OptionGroup id={"exchange-initiation-documents-card-option-group-1"}
                         label="Documents">
                {Object.values(DocumentType).map((option) => (
                    <Option id={`exchange-initiation-documents-card-option-1-${option}`}
                            key={option}
                            value={option}>
                        {option}
                    </Option>
                ))}
            </OptionGroup>
            <OptionGroup id={"exchange-initiation-documents-card-option-group-2"}
                         label="Images">
                {Object.values(ImageType).map((option) => (
                    <Option id={`exchange-initiation-documents-card-option-2-${option}`}
                            key={option}
                            value={option}>
                        {option}
                    </Option>
                ))}
            </OptionGroup>
        </Dropdown>
    );
};

export default DocumentTypeDropdown;
