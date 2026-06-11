import {Divider, Radio, RadioGroup, Switch, Text} from "@fluentui/react-components";
import {useAppSettingsTabStyles} from "./AppSettingsTabStyles.tsx";
import React, {useEffect, useState} from "react";
import {fetchAppUser, updateAppUserSettings} from "../../../services/appUserApi";
import {AppUserSettingsDto} from "../../models/models.tsx";
import {useAuth} from "../../../context/AuthContext.tsx";
import {useTheme} from "../../../context/themeContextBase";
import type {ThemeMode} from "../../../context/theme";

const AppSettingsTab = () =>
{
    const styles = useAppSettingsTabStyles();
    const {token, appUser, setAppUser} = useAuth();
    const {mode: themeMode, setMode: setThemeMode} = useTheme();

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
                    // Don't call setThemeMode here exch- ThemeSync already
                    // handles the merge between localStorage and DB on
                    // sign-in. Calling it here would override the local
                    // preference that ThemeSync deliberately preserved.
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
    }, [token, setThemeMode]);

    const handleSettingChange = async (setting: keyof AppUserSettingsDto, value: AppUserSettingsDto[keyof AppUserSettingsDto]) =>
    {
        if (!settings)
        {
            return;
        }
        const previousSettings = settings;
        const updatedSettings = {...settings, [setting]: value} as AppUserSettingsDto;
        try
        {
            setSettings(updatedSettings);
            await updateAppUserSettings(updatedSettings, token);
            // Keep the cached appUser in sync so ThemeSync (and others) see the update.
            if (appUser)
            {
                setAppUser({...appUser, settings: updatedSettings});
            }
        }
        catch (error)
        {
            console.error(`Failed to update ${String(setting)}:`, error);
            // Revert the setting on error
            setSettings(previousSettings);
        }
    };

    const handleThemeChange = async (next: ThemeMode) =>
    {
        // Apply immediately for snappy UX; revert in handleSettingChange on failure.
        const previousMode = themeMode;
        setThemeMode(next);
        try
        {
            await handleSettingChange("theme", next);
        }
        catch
        {
            setThemeMode(previousMode);
        }
    };

    return (
        <div className={styles.container}>

            <Divider appearance="brand"
                     alignContent="start"
                     className={styles.mainDivider}>
                Appearance
            </Divider>

            <RadioGroup
                value={themeMode}
                onChange={(_, data) => handleThemeChange(data.value as ThemeMode)}
                layout="horizontal"
                aria-label="Theme"
                disabled={isLoading}
            >
                <Radio value="light" label="Light"/>
                <Radio value="dark" label="Dark"/>
                <Radio value="system" label="System default"/>
            </RadioGroup>

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
                    label="Get notifications on Exchange Initiation"
                    checked={settings.notifyShareStart}
                    onChange={(_, data) => handleSettingChange('notifyShareStart', !!data.checked)}
                    disabled={isLoading}
                />

                <Switch
                    label="Get notifications on Exchange Accepted"
                    checked={settings.notifyShareAccept}
                    onChange={(_, data) => handleSettingChange('notifyShareAccept', !!data.checked)}
                    disabled={isLoading}
                />

                <Switch
                    label="Get notifications on Exchange Declined"
                    checked={settings.notifyShareDecline}
                    onChange={(_, data) => handleSettingChange('notifyShareDecline', !!data.checked)}
                    disabled={isLoading}
                />

                <Switch
                    label="Get notifications on Exchange End"
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


