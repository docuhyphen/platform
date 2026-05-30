import {useEffect} from "react";
import {useAuth} from "../../context/AuthContext.tsx";
import {useTheme} from "../../context/themeContextBase";
import type {ThemeMode} from "../../context/theme";

/**
 * Mirrors the signed-in user's stored theme preference into the
 * in-memory ThemeContext, and resets to "light" on sign-out.
 * Must be rendered inside both AuthProvider and ThemeProvider.
 */
const ThemeSync = () =>
{
    const {appUser} = useAuth();
    const {setMode} = useTheme();

    useEffect(() =>
    {
        const stored = appUser?.settings?.theme;
        if (stored === "light" || stored === "dark" || stored === "system")
        {
            setMode(stored as ThemeMode);
        }
        else
        {
            setMode("light");
        }
    }, [appUser?.settings?.theme, setMode]);

    return null;
};

export default ThemeSync;


