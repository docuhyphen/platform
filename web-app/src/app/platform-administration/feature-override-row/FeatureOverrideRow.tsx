import {Button, Switch, Text} from "@fluentui/react-components";
import {DeleteRegular} from "@fluentui/react-icons";
import {PlatformFeatureEntitlement} from "../../../services/types/platformFeatureEntitlements.ts";
import {featureOverrideLabel} from "../feature-override-selector/featureOverrideLabels.ts";
import {useFeatureOverrideRowStyles} from "./FeatureOverrideRowStyles.tsx";

interface FeatureOverrideRowProps {
    idPrefix: string;
    index: number;
    entitlement: PlatformFeatureEntitlement;
    disabled: boolean;
    onChange: (index: number, entitlement: PlatformFeatureEntitlement) => void;
    onRemove: (index: number) => void;
}

const FeatureOverrideRow = ({idPrefix, index, entitlement, disabled, onChange, onRemove}: FeatureOverrideRowProps) => {
    const styles = useFeatureOverrideRowStyles();
    const label = featureOverrideLabel(entitlement.featureCode);

    return (
        <div id={`${idPrefix}-entitlement-${index}`}
             className={styles.entitlementRow}>
            <Text id={`${idPrefix}-entitlement-code-${index}`}
                  className={styles.entitlementName}>
                {label}
            </Text>
            <Switch id={`${idPrefix}-entitlement-enabled-${index}`}
                    label={"Enabled"}
                    aria-label={`${label} enabled`}
                    checked={entitlement.enabled}
                    disabled={disabled}
                    onChange={(_, data) => onChange(index, {...entitlement, enabled: data.checked})}/>
            <Button id={`${idPrefix}-entitlement-remove-${index}`}
                    aria-label={`Remove the ${label} override`}
                    appearance={"subtle"}
                    shape={"circular"}
                    icon={<DeleteRegular/>}
                    disabled={disabled}
                    onClick={() => onRemove(index)}/>
        </div>
    );
};

export default FeatureOverrideRow;
