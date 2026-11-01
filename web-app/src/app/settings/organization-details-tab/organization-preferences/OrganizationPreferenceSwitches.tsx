import {Switch} from "@fluentui/react-components";
import {OrganizationSettingsDto} from "../../../models/models.tsx";

interface OrganizationPreferenceSwitchesProps
{
    settings: OrganizationSettingsDto;
    saving: boolean;
    onSettingChange: (setting: keyof OrganizationSettingsDto, value: boolean) => void;
}

const OrganizationPreferenceSwitches = ({settings, saving, onSettingChange}: OrganizationPreferenceSwitchesProps) => (
    <>
        <Switch
            id={"switch-discoverable-for-trust-requests"}
            checked={settings.discoverableForTrustRequests}
            onChange={(_, data) => onSettingChange('discoverableForTrustRequests', data.checked)}
            label="Allow verified organizations to find us for trust requests"
            disabled={saving}
        />
        <Switch
            id={"switch-allow-external-customer-sharing"}
            checked={settings.allowExternalCustomerSharing !== false}
            onChange={(_, data) => onSettingChange('allowExternalCustomerSharing', data.checked)}
            label="Allow sharing with external customers (individuals)"
            disabled={saving}
        />
        <Switch
            id={"switch-require-trusted-organization-for-b2b"}
            checked={settings.requireTrustedOrganizationForB2b}
            onChange={(_, data) => onSettingChange('requireTrustedOrganizationForB2b', data.checked)}
            label="Require a trusted organization for sharing with other organizations"
            disabled={saving}
        />
        <Switch
            id={"switch-allow-profile-update"}
            checked={settings.allowProfileUpdate}
            onChange={(_, data) => onSettingChange('allowProfileUpdate', data.checked)}
            label="Allow users to update their basic profiles"
            disabled={saving}
        />
        <Switch
            id={"switch-allow-email-update"}
            checked={settings.allowEmailUpdate}
            onChange={(_, data) => onSettingChange('allowEmailUpdate', data.checked)}
            label="Allow users to update their email addresses"
            disabled={saving}
        />
    </>
);

export default OrganizationPreferenceSwitches;
