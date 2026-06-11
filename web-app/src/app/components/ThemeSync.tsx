import {useEffect, useRef} from "react";
import {useAuth} from "../../context/AuthContext.tsx";
import {useTheme} from "../../context/themeContextBase";
import type {ThemeMode} from "../../context/theme";
import {updateAppUserSettings} from "../../services/appUserApi";

const THEME_STORAGE_KEY = "docuhyphen:theme:mode";
const isValidMode = (v: unknown): v is ThemeMode =>
    v === "light" || v === "dark" || v === "system";

/**
 * Keeps the in-memory ThemeContext, localStorage, and the server-side
 * user-settings in sync on sign-in.
 *
 * Priority rules (evaluated once per sign-in, not on every render):
 *   1. If localStorage already holds a theme the user chose on this
 *      device, that value wins exch- it is pushed to the DB so the
 *      preference follows the user to other devices.
 *   2. Otherwise the DB value is applied locally (and written to
 *      localStorage via setMode).
 *
 * This means a user who picked "dark" while signed-out (login page)
 * will keep dark mode after signing in, even though the DB default
 * is "light".
 *
 * Subsequent explicit theme changes go through the Settings page,
 * which calls setMode (ThemeContext + localStorage) and
 * updateAppUserSettings (DB) directly exch- ThemeSync does not need to
 * react to those.
 *
 * Must be rendered inside both AuthProvider and ThemeProvider.
 */
const ThemeSync = () =>
{
    const {appUser, token, setAppUser} = useAuth();
    const {setMode} = useTheme();

    // Track which appUser.id we've already synced so we only run the
    // merge logic once per sign-in, not on every re-render.
    const syncedUserIdRef = useRef<string | null>(null);

    useEffect(() =>
    {
        if (!appUser?.id || !appUser.settings)
        {
            // Not signed in (yet) exch- nothing to sync.
            // Reset so we re-merge on the next sign-in.
            syncedUserIdRef.current = null;
            return;
        }

        // Only run the merge once per user session.
        if (syncedUserIdRef.current === appUser.id)
        {
            return;
        }
        syncedUserIdRef.current = appUser.id;

        const dbTheme = appUser.settings.theme;
        const localTheme = window.localStorage.getItem(THEME_STORAGE_KEY);

        if (isValidMode(localTheme) && localTheme !== dbTheme)
        {
            // localStorage wins exch- apply it locally and push to DB.
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
            // No local preference (or already matches) exch- apply the DB
            // value locally (also writes to localStorage via setMode).
            setMode(dbTheme as ThemeMode);
        }
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [appUser?.id]);

    return null;
};

export default ThemeSync;


