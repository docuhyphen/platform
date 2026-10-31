import {Dropdown, Field, Option} from "@fluentui/react-components";
import {PlanFeature} from "../../models/models.tsx";
import {featureOverrideLabels} from "./featureOverrideLabels.ts";
import {useFeatureOverrideSelectorStyles} from "./FeatureOverrideSelectorStyles.tsx";

interface FeatureOverrideSelectorProps {
    idPrefix: string;
    ownerDescription: string;
    features?: PlanFeature[];
    selectedFeatures: string[];
    disabled: boolean;
    onChange: (featureCodes: string[]) => void;
}

const FeatureOverrideSelector = ({
                                     idPrefix,
                                     ownerDescription,
                                     features = Object.values(PlanFeature),
                                     selectedFeatures,
                                     disabled,
                                     onChange,
                                 }: FeatureOverrideSelectorProps) => {
    const styles = useFeatureOverrideSelectorStyles();
    const selectedLabel = selectedFeatures.length === 0
        ? "Select features"
        : `${selectedFeatures.length} feature${selectedFeatures.length === 1 ? "" : "s"} selected`;

    return (
        <Field id={`${idPrefix}-feature-selector-field`}
               label={"Features"}
               hint={`Select features to configure for ${ownerDescription}`}>
            <Dropdown id={`${idPrefix}-feature-selector`}
                      className={styles.featureDropdown}
                      multiselect={true}
                      appearance={"outline"}
                      disabled={disabled}
                      selectedOptions={selectedFeatures}
                      onOptionSelect={(_, data) => onChange(data.selectedOptions)}
                      button={{children: selectedLabel}}>
                {features.map(feature => (
                    <Option key={feature}
                            id={`${idPrefix}-feature-option-${feature.toLowerCase()}`}
                            value={feature}
                            text={featureOverrideLabels[feature]}>
                        {featureOverrideLabels[feature]}
                    </Option>
                ))}
            </Dropdown>
        </Field>
    );
};

export default FeatureOverrideSelector;
