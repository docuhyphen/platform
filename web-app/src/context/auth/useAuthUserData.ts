import {useEffect} from 'react';
import {NavigateFunction} from "react-router-dom";
import {fetchAppUser, fetchAppUserPersonOrganization, fetchCurrentSession} from '../../services/appUserApi.ts';
import {fetchAppUserAvatarObjectUrl} from '../../services/appUserAvatarApi.ts';
import {isTokenExpired} from "../../utils/helpers.ts";
import {responseStatusOf} from "../../services/requestFailure.ts";
import {AuthSessionState} from "./useAuthSessionState.ts";

interface AuthUserDataOptions
{
    accessToken: string | null;
    setAccessToken: (token: string | null) => void;
    setIdToken: (token: string | null) => void;
    session: AuthSessionState;
    navigate: NavigateFunction;
    pathname: string;
}

const PUBLIC_PATHS = ["/sign-in", "/sign-up", "/account-recovery", "/nas", "/app-session-expired", "/oauth/callback", "/oauth/link-confirm"];

export const useAuthUserData = (options: AuthUserDataOptions) =>
{
    const {accessToken, setAccessToken, setIdToken, session, navigate, pathname} = options;
    const {
        appUser,
        setAppUser,
        appUserPersonOrganization,
        setAppUserPersonOrganization,
        currentSession,
        setCurrentSession,
        applyOrgResolution,
    } = session;

    const redirectToLogin = () =>
    {
        if (!PUBLIC_PATHS.includes(pathname))
        {
            navigate("/sign-in");
        }
    };

    const loadPersonOrganization = async () =>
    {
        if (appUser.organizationRoles.length === 0)
        {
            return;
        }
        try
        {
            setAppUserPersonOrganization(await fetchAppUserPersonOrganization(appUser?.id, appUser?.person?.id, accessToken));
        }
        catch (error: unknown)
        {
            if (responseStatusOf(error) === 404)
            {
                console.log("Organization not found for user");
            }
        }
    };

    const loadCurrentSession = async () =>
    {
        try
        {
            const fetched = await fetchCurrentSession();
            setCurrentSession(fetched);
            applyOrgResolution(fetched);
        }
        catch (error: unknown)
        {
            console.error("Failed to fetch current session:", error);
        }
    };

    const fetchUserData = async () =>
    {
        try
        {
            if (!appUser && accessToken)
            {
                const user = await fetchAppUser(accessToken);
                setAppUser(user);
            }

            if (appUser && !appUser.person)
            {
                navigate("/onboarding/individual");
                return;
            }

            if (appUser?.person && !appUserPersonOrganization && accessToken)
            {
                await loadPersonOrganization();
            }

            if (appUser && !currentSession)
            {
                await loadCurrentSession();
            }
        }
        catch (error: unknown)
        {
            console.error("Failed to fetch user data:", error);
            setAccessToken(null);
            setIdToken(null);
            redirectToLogin();
        }
    };

    useEffect(() =>
    {
        if (!accessToken)
        {
            setAppUser(null);
            setAppUserPersonOrganization(null);
            setCurrentSession(null);
            return;
        }

        if (isTokenExpired(accessToken))
        {
            return;
        }

        fetchUserData();
    }, [accessToken, appUser, pathname]);

    useEffect(() =>
    {
        if (!appUser?.id || !accessToken)
        {
            return;
        }

        const marker = appUser.avatarUrl;
        if (!marker || marker.startsWith("blob:"))
        {
            return;
        }

        let cancelled = false;
        const loadAvatar = async () =>
        {
            const objectUrl = await fetchAppUserAvatarObjectUrl(accessToken);
            if (cancelled)
            {
                if (objectUrl)
                {
                    URL.revokeObjectURL(objectUrl);
                }
                return;
            }
            setAppUser(prev => (prev ? {...prev, avatarUrl: objectUrl} : prev));
        };
        void loadAvatar().catch((): undefined => undefined);

        return () =>
        {
            cancelled = true;
        };
    }, [appUser?.id, appUser?.avatarUrl, accessToken]);
};
