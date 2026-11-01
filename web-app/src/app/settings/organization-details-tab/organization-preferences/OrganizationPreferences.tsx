import {Divider} from "@fluentui/react-components";
import {OrganizationSettingsDto} from "../../../models/models.tsx";
import OrganizationPreferenceSwitches from "./OrganizationPreferenceSwitches.tsx";
import OrganizationPreferencesSummary from "./OrganizationPreferencesSummary.tsx";
import {useOrganizationPreferencesStyles} from "./OrganizationPreferencesStyles.tsx";

interface OrganizationPreferencesProps
{
    settings: OrganizationSettingsDto | null;
    canManageOrganization: boolean;
    saving: boolean;
    onSettingChange: (setting: keyof OrganizationSettingsDto, value: boolean) => void;
}

const OrganizationPreferences = ({settings, canManageOrganization, saving, onSettingChange}: OrganizationPreferencesProps) =>
{
    const styles = useOrganizationPreferencesStyles();

    return (
        <>
            <Divider id={"organization-details-tab-divider-2"}
                     alignContent="start"
                     appearance="brand"
                     className={styles.mainDivider}>
                Organization Preferences
            </Divider>

            {settings && canManageOrganization && (
                <OrganizationPreferenceSwitches settings={settings}
                                                saving={saving}
                                                onSettingChange={onSettingChange}/>
            )}

            {settings && !canManageOrganization && (
                <OrganizationPreferencesSummary settings={settings}/>
            )}
        </>
    );
};

export default OrganizationPreferences;
