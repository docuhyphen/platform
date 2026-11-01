import React from "react";
import {
    Dropdown,
    Field,
    Option,
    OptionGroup,
    OptionOnSelectData,
    SelectionEvents,
    Switch
} from "@fluentui/react-components";
import {DocumentType, ImageType} from "../../../../models/models.tsx";
import {useDocumentRestrictionControlsStyles} from "./DocumentRestrictionControlsStyles.tsx";

interface DocumentRestrictionControlsProps
{
    idPrefix: string;
    switchId: string;
    dropdownId: string;
    restrictionEnabled: boolean;
    selectedType: string | undefined;
    onRestrictionToggle: (enabled: boolean) => void;
    onOptionSelect: (event: SelectionEvents, data: OptionOnSelectData) => void;
}

const DocumentRestrictionControls: React.FC<DocumentRestrictionControlsProps> = (
    {
        idPrefix,
        switchId,
        dropdownId,
        restrictionEnabled,
        selectedType,
        onRestrictionToggle,
        onOptionSelect
    }) =>
{
    const styles = useDocumentRestrictionControlsStyles();

    return (
        <div id={`${idPrefix}-div-1`}
             className={styles.documentRestriction}>
            <Field id={`${idPrefix}-field-2`}
                   label="">
                <Switch id={switchId}
                        label="Restrict upload type"
                        checked={restrictionEnabled}
                        onChange={(ev) => onRestrictionToggle(ev.target.checked)}/>
            </Field>
            <Dropdown id={dropdownId}
                      disabled={!restrictionEnabled}
                      value={selectedType}
                      placeholder="Select allowed upload type"
                      onOptionSelect={onOptionSelect}>
                <OptionGroup id={`${idPrefix}-option-group-1`}
                             label="Documents">
                    {Object.values(DocumentType).map((option) => (
                        <Option id={`${idPrefix}-option-1-${option}`}
                                key={option}
                                value={option}>
                            {option}
                        </Option>
                    ))}
                </OptionGroup>
                <OptionGroup id={`${idPrefix}-option-group-2`}
                             label="Images">
                    {Object.values(ImageType).map((option) => (
                        <Option id={`${idPrefix}-option-2-${option}`}
                                key={option}
                                value={option}>
                            {option}
                        </Option>
                    ))}
                </OptionGroup>
            </Dropdown>
        </div>
    );
};

export default DocumentRestrictionControls;
