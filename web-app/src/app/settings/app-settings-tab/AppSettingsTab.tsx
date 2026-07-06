import {Divider, Radio, RadioGroup, Switch} from "@fluentui/react-components";
import {useAppSettingsTabStyles} from "./AppSettingsTabStyles.tsx";
import {useEffect, useState} from "react";
import {fetchAppUser, updateAppUserSettings} from "../../../services/appUserApi";
import {AppUserSettingsDto, NotificationPreferenceChannel} from "../../models/models.tsx";
import {useAuth} from "../../../context/AuthContext.tsx";
import {useTheme} from "../../../context/themeContextBase";
import type {ThemeMode} from "../../../context/theme";
import NotificationPreferenceRow from "./notification-preference-row/NotificationPreferenceRow.tsx";
import {
    getNotificationChannels,
    NotificationPreferenceDefinition,
    notificationPreferenceDefinitions
} from "./notificationPreferenceDefinitions.ts";
import BrowserNotificationsCard from "./browser-notifications-card/BrowserNotificationsCard.tsx";

const AppSettingsTab = () => {
    const styles = useAppSettingsTabStyles();
    const {token, appUser, setAppUser} = useAuth();
    const {mode: themeMode, setMode: setThemeMode} = useTheme();
    const [settings, setSettings] = useState<AppUserSettingsDto>();
    const [isLoading, setIsLoading] = useState(true);
    const [isSaving, setIsSaving] = useState(false);

    useEffect(() => {
        const loadSettings = async () => {
            try {
                setIsLoading(true);
                const userData = await fetchAppUser(token);
                if (userData.settings) setSettings(userData.settings);
            } catch (error) {
                console.error("Failed to load user settings:", error);
            } finally {
                setIsLoading(false);
            }
        };
        loadSettings();
    }, [token]);

    const saveSettings = async (changes: Partial<AppUserSettingsDto>) => {
        if (!settings) return;
        const previousSettings = settings;
        const updatedSettings = {...settings, ...changes};
        try {
            setIsSaving(true);
            setSettings(updatedSettings);
            await updateAppUserSettings(updatedSettings, token);
            if (appUser) setAppUser({...appUser, settings: updatedSettings});
        } catch (error) {
            console.error("Failed to update user settings:", error);
            setSettings(previousSettings);
            throw error;
        } finally {
            setIsSaving(false);
        }
    };

    const handleThemeChange = async (next: ThemeMode) => {
        const previousMode = themeMode;
        setThemeMode(next);
        try {
            await saveSettings({theme: next});
        } catch {
            setThemeMode(previousMode);
        }
    };

    const handleNotificationChannelsChange = async (
        definition: NotificationPreferenceDefinition,
        channels: NotificationPreferenceChannel[],
    ) => {
        try {
            await saveSettings({
                [definition.channelSetting]: channels,
                [definition.legacySetting]: channels.length > 0,
            });
        } catch {
            // saveSettings restores the previous selection.
        }
    };

    const controlsDisabled = isLoading || isSaving;

    return (
        <div id="app-preferences"
             className={styles.container}>
            <Divider id="appearance-preferences-divider"
                     appearance="brand"
                     alignContent="start"
                     className={styles.mainDivider}>
                Appearance
            </Divider>
            <RadioGroup id="radiogroup-theme"
                        value={themeMode}
                        onChange={(_, data) => handleThemeChange(data.value as ThemeMode)}
                        layout="horizontal"
                        aria-label="Theme"
                        disabled={controlsDisabled}>
                <Radio id="theme-light"
                       value="light"
                       label="Light"/>
                <Radio id="theme-dark"
                       value="dark"
                       label="Dark"/>
                <Radio id="theme-system"
                       value="system"
                       label="System default"/>
            </RadioGroup>
            {settings && (
                <>
                    <Switch id="switch-auto-preview-documents"
                            label="Automatically preview documents"
                            checked={settings.autoPreviewDocuments}
                            onChange={(_, data) =>
                                saveSettings({autoPreviewDocuments: !!data.checked}).catch(() => undefined)}
                            disabled={controlsDisabled}/>
                    <Divider id="notification-preferences-divider"
                             appearance="brand"
                             alignContent="start"
                             className={styles.mainDivider}>
                        Notifications
                    </Divider>
                    <div id="notification-preferences-list"
                         className={styles.notificationList}>
                        <BrowserNotificationsCard/>
                        {notificationPreferenceDefinitions.map(definition => (
                            <NotificationPreferenceRow key={definition.id}
                                                       id={definition.id}
                                                       title={definition.title}
                                                       description={definition.description}
                                                       channels={getNotificationChannels(settings, definition)}
                                                       disabled={controlsDisabled}
                                                       onChange={channels =>
                                                           handleNotificationChannelsChange(definition, channels)}/>
                        ))}
                    </div>
                </>
            )}
        </div>
    );
};

export default AppSettingsTab;
