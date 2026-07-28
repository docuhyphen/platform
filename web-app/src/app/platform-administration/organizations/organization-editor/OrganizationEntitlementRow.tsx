import {Button, Input, Switch} from "@fluentui/react-components";
import {DeleteRegular} from "@fluentui/react-icons";
import {PlatformOrganizationFeatureEntitlement} from "../../../../services/types/platformOrganizations.ts";
import {useOrganizationEditorStyles} from "./OrganizationEditorStyles.tsx";

interface OrganizationEntitlementRowProps
{
    index: number;
    entitlement: PlatformOrganizationFeatureEntitlement;
    disabled: boolean;
    onChange: (index: number, entitlement: PlatformOrganizationFeatureEntitlement) => void;
    onRemove: (index: number) => void;
}

const OrganizationEntitlementRow = ({
    index,
    entitlement,
    disabled,
    onChange,
    onRemove,
}: OrganizationEntitlementRowProps) =>
{
    const styles = useOrganizationEditorStyles();

    return (
        <div
            id={`platform-organization-entitlement-${index}`}
            className={styles.entitlementRow}>
            <Input
                id={`platform-organization-entitlement-code-${index}`}
                aria-label={`Feature code ${index + 1}`}
                value={entitlement.featureCode}
                disabled={disabled}
                placeholder={"FEATURE_CODE"}
                onChange={(_, data) => onChange(index, {
                    ...entitlement,
                    featureCode: data.value.toUpperCase(),
                })}/>
            <Switch
                id={`platform-organization-entitlement-enabled-${index}`}
                label={entitlement.enabled ? "Enabled" : "Disabled"}
                checked={entitlement.enabled}
                disabled={disabled}
                onChange={(_, data) => onChange(index, {
                    ...entitlement,
                    enabled: data.checked,
                })}/>
            <Button
                id={`platform-organization-entitlement-remove-${index}`}
                aria-label={`Remove feature ${index + 1}`}
                appearance={"subtle"}
                shape={"circular"}
                icon={<DeleteRegular/>}
                disabled={disabled}
                onClick={() => onRemove(index)}/>
        </div>
    );
};

export default OrganizationEntitlementRow;
