import {
    Dropdown,
    Field,
    Option,
    OptionOnSelectData,
    SelectionEvents,
} from "@fluentui/react-components";
import {PlanFeature} from "../../../models/models.tsx";
import {useOrganizationEditorStyles} from "./OrganizationEditorStyles.tsx";

interface OrganizationFeatureSelectorProps
{
    selectedFeatures: string[];
    disabled: boolean;
    onChange: (featureCodes: string[]) => void;
}

const featureLabels: Record<PlanFeature, string> = {
    [PlanFeature.EXCHANGE_CREATE]: "Exchange creation",
    [PlanFeature.MULTIPLE_PARTICIPANTS]: "Multiple participants",
    [PlanFeature.BLUEPRINT_USE]: "Blueprint use",
    [PlanFeature.BLUEPRINT_MANAGE]: "Blueprint management",
    [PlanFeature.DOCUMENT_LIBRARY_USE]: "Document Library use",
    [PlanFeature.DOCUMENT_LIBRARY_MANAGE]: "Document Library management",
    [PlanFeature.DOCUMENT_COMMENTS]: "Document comments",
    [PlanFeature.DOCUMENT_VERSION_HISTORY]: "Document version history",
    [PlanFeature.ADVANCED_ACCESS_CONTROLS]: "Advanced access controls",
    [PlanFeature.VARIABLES_AND_SEQUENCES]: "Variables and sequences",
    [PlanFeature.PERSONAL_REMINDERS]: "Personal reminders",
    [PlanFeature.BUSINESS_FIELDS_AND_SCHEMAS]: "Business fields and schemas",
    [PlanFeature.WORKFLOW_AUTOMATION]: "Workflow automation",
    [PlanFeature.ORGANIZATION_ADMINISTRATION]: "Organization administration",
    [PlanFeature.AUDIT_GOVERNANCE]: "Audit governance",
    [PlanFeature.IDENTITY_AND_INTEGRATIONS]: "Identity and integrations",
};

const organizationFeatureOptions = Object.values(PlanFeature);

const OrganizationFeatureSelector = ({
    selectedFeatures,
    disabled,
    onChange,
}: OrganizationFeatureSelectorProps) =>
{
    const styles = useOrganizationEditorStyles();
    const selectedLabel = selectedFeatures.length === 0
        ? "Select features"
        : `${selectedFeatures.length} feature${selectedFeatures.length === 1 ? "" : "s"} selected`;
    const onOptionSelect = (_event: SelectionEvents, data: OptionOnSelectData) =>
        onChange(data.selectedOptions);

    return (
        <Field
            id={"platform-organization-feature-selector-field"}
            label={"Features"}
            hint={"Select features to configure for this organization"}>
            <Dropdown
                id={"platform-organization-feature-selector"}
                className={styles.featureDropdown}
                aria-label={"Organization feature overrides"}
                multiselect
                appearance={"outline"}
                disabled={disabled}
                selectedOptions={selectedFeatures}
                onOptionSelect={onOptionSelect}
                button={{children: selectedLabel}}>
                {organizationFeatureOptions.map((feature) => (
                    <Option
                        key={feature}
                        id={`platform-organization-feature-option-${feature.toLowerCase()}`}
                        value={feature}
                        text={featureLabels[feature]}>
                        {featureLabels[feature]}
                    </Option>
                ))}
            </Dropdown>
        </Field>
    );
};

export default OrganizationFeatureSelector;
