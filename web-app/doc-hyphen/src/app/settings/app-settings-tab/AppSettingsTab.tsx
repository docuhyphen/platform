import {Divider, Switch} from "@fluentui/react-components";
import {useAppSettingsTabStyles} from "./AppSettingsTabStyles.tsx";
import {useEffect, useState} from "react";
import {fetchAppUser, updateAppUserSettings} from "../../../services/appUserApi";
import {AppUserSettingsDto} from "../../models/models.tsx";
import {useAuth} from "../../../context/AuthContext.tsx";

const AppSettingsTab = () =>
{
    const styles = useAppSettingsTabStyles();
    const {token} = useAuth();

    const [settings, setSettings] = useState<AppUserSettingsDto>();
    const [isLoading, setIsLoading] = useState(true);

    useEffect(() =>
    {
        const loadSettings = async () =>
        {
            try
            {
                setIsLoading(true);
                const userData = await fetchAppUser(token);
                if (userData.settings)
                {
                    setSettings(userData.settings);
                }
            }
            catch (error)
            {
                console.error("Failed to load user settings:", error);
            }
            finally
            {
                setIsLoading(false);
            }
        };

        loadSettings();
    }, [token]);

    const handleSettingChange = async (setting: keyof AppUserSettingsDto, value: boolean) =>
    {
        try
        {
            const updatedSettings = {...settings, [setting]: value};
            setSettings(updatedSettings);
            await updateAppUserSettings(updatedSettings, token);
        }
        catch (error)
        {
            console.error(`Failed to update ${setting}:`, error);
            // Revert the setting on error
            setSettings(settings);
        }
    };

    return (
        <div className={styles.container}>

            {settings && <>
                <Switch
                    label="Automatically preview documents"
                    checked={settings.autoPreviewDocuments}
                    onChange={(_, data) => handleSettingChange('autoPreviewDocuments', !!data.checked)}
                    disabled={isLoading}
                />

                <Divider appearance="brand"
                         alignContent="start"
                className={styles.mainDivider}>
                    Notifications
                </Divider>

                <Switch
                    label="Get notifications on Sharing Session Initiation"
                    checked={settings.notifyShareStart}
                    onChange={(_, data) => handleSettingChange('notifyShareStart', !!data.checked)}
                    disabled={isLoading}
                />

                <Switch
                    label="Get notifications on Sharing Session Accepted"
                    checked={settings.notifyShareAccept}
                    onChange={(_, data) => handleSettingChange('notifyShareAccept', !!data.checked)}
                    disabled={isLoading}
                />

                <Switch
                    label="Get notifications on Sharing Session Declined"
                    checked={settings.notifyShareDecline}
                    onChange={(_, data) => handleSettingChange('notifyShareDecline', !!data.checked)}
                    disabled={isLoading}
                />

                <Switch
                    label="Get notifications on Sharing Session End"
                    checked={settings.notifyShareEnd}
                    onChange={(_, data) => handleSettingChange('notifyShareEnd', !!data.checked)}
                    disabled={isLoading}
                />

                <Switch
                    label="Get notifications on document notes/comments"
                    checked={settings.notifyDocComment}
                    onChange={(_, data) => handleSettingChange('notifyDocComment', !!data.checked)}
                    disabled={isLoading}
                />

                <Switch
                    label="Get notifications on document deletions"
                    checked={settings.notifyDocDelete}
                    onChange={(_, data) => handleSettingChange('notifyDocDelete', !!data.checked)}
                    disabled={isLoading}
                />

                <Switch
                    label="Get notifications on document additions"
                    checked={settings.notifyDocAdd}
                    onChange={(_, data) => handleSettingChange('notifyDocAdd', !!data.checked)}
                    disabled={isLoading}
                />

                <Switch
                    label="Get notifications on document upload"
                    checked={settings.notifyDocUpload}
                    onChange={(_, data) => handleSettingChange('notifyDocUpload', !!data.checked)}
                    disabled={isLoading}
                />
            </>
            }
        </div>
    );
};

export default AppSettingsTab;