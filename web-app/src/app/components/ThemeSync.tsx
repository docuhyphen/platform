import {useEffect, useRef} from "react";
import {useAuth} from "../../context/AuthContext.tsx";
import {useTheme} from "../../context/themeContextBase";
import type {ThemeMode} from "../../context/theme";
import {updateAppUserSettings} from "../../services/appUserApi";

const THEME_STORAGE_KEY = "docuhyphen:theme:mode";
const isValidMode = (v: unknown): v is ThemeMode =>
    v === "light" || v === "dark" || v === "system";

const ThemeSync = (): null =>
{
    const {appUser, token, setAppUser} = useAuth();
    const {setMode} = useTheme();

    const syncedUserIdRef = useRef<string | null>(null);

    useEffect(() =>
    {
        if (!appUser?.id || !appUser.settings)
        {
            syncedUserIdRef.current = null;
            return;
        }

        if (syncedUserIdRef.current === appUser.id)
        {
            return;
        }
        syncedUserIdRef.current = appUser.id;

        const dbTheme = appUser.settings.theme;
        const localTheme = window.localStorage.getItem(THEME_STORAGE_KEY);

        if (isValidMode(localTheme) && localTheme !== dbTheme)
        {
            setMode(localTheme);

            const updatedSettings = {...appUser.settings, theme: localTheme};
            updateAppUserSettings(updatedSettings, token).then(() =>
            {
                setAppUser({...appUser, settings: updatedSettings});
            }).catch((err) =>
            {
                console.warn("ThemeSync: failed to push local theme to server", err);
            });
        }
        else if (isValidMode(dbTheme))
        {
            setMode(dbTheme as ThemeMode);
        }
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [appUser?.id]);

    return null;
};

export default ThemeSync;

