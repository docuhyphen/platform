import React, {createContext, ReactNode, useContext, useEffect, useRef, useState} from 'react';
import {fetchAppUser, fetchAppUserPersonOrganization} from '../services/userApi.ts';
import {AppUserDetailedDto, OrganizationBasicDto, OrganizationDetailedDto} from "../app/models/models.tsx";
import {isTokenExpired} from "../utils/helpers.ts";
import {useLocation, useNavigate} from "react-router-dom";
import {setApiClientAuthToken} from "../services/apiClient.ts";

interface AuthContextType
{
    token: string | null;
    setToken: (token: string | null) => void;
    appUser: AppUserDetailedDto | null;
    setAppUser: (user: AppUserDetailedDto | null) => void;
    appUserPersonOrganization: OrganizationDetailedDto | null;
    setAppUserPersonOrganization: (organization: OrganizationDetailedDto | null) => void;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

export const AuthProvider: React.FC<{ children: ReactNode }> = ({children}) =>
{
    const navigate = useNavigate();
    const location = useLocation();
    const [token, setToken] = useState<string | null>(() => localStorage.getItem('token'));
    const tokenExpirationIntervalRef = useRef<number | null>(null);
    const [appUser, setAppUser] = useState<AppUserDetailedDto | null>(null);
    const [appUserPersonOrganization, setAppUserPersonOrganization] = useState<OrganizationDetailedDto | null>(null);

    useEffect(() =>
    {
        tokenExpirationIntervalRef.current = window.setInterval(() =>
        {
            setToken((currentToken) =>
            {
                if (currentToken && isTokenExpired(currentToken))
                {
                    alert("Session expired");
                    saveToken(null);
                    redirectToSessionExpired();
                    return null;
                }

                return currentToken;
            });
        }, 5000);

        return () =>
        {
            if (tokenExpirationIntervalRef.current)
            {
                clearInterval(tokenExpirationIntervalRef.current);
            }
        };
    }, []);

    useEffect(() =>
    {
        if (token === null)
        {
            localStorage.removeItem('token');
            setApiClientAuthToken(null);
        }
        else
        {
            localStorage.setItem('token', token);
            setApiClientAuthToken(token);
        }
    }, [token]);

    useEffect(() =>
    {
        if (!token)
        {
            setAppUser(null);
            setAppUserPersonOrganization(null);
            return;
        }

        if (isTokenExpired(token))
        {
            redirectToSessionExpired();
            return;
        }

        fetchUserData();
    }, [token, location.pathname]);

    const fetchUserData = async () =>
    {
        try
        {
            if (!appUser && token)
            {
                const user = await fetchAppUser(token);
                setAppUser(user);
            }

            if (appUser && !appUser.person)
            {
                navigate("/onboarding/individual");
                return;
            }

            if (appUser?.person && !appUserPersonOrganization && token)
            {
                try
                {
                    setAppUserPersonOrganization(await fetchAppUserPersonOrganization(appUser?.id, appUser?.person?.id, token));
                }
                catch (error: any)
                {
                    if (error.response?.status === 404)
                    {
                        console.log("Organization not found for user");
                    }
                }
            }
        }
        catch (error: any)
        {
            console.error("Failed to fetch user data:", error);
            saveToken(null);
            redirectToLogin();
        }
    };

    const saveToken = (newToken: string | null) =>
    {
        setToken(newToken);
        if (newToken)
        {
            localStorage.setItem("token", newToken);
        }
        else
        {
            localStorage.removeItem("token");
            setAppUser(null);
            setAppUserPersonOrganization(null);
        }
    };

    const redirectToLogin = () =>
    {
        if (!["/sign-in", "/sign-up", "/account-recovery", "/nas", "/app-session-expired"].includes(location.pathname))
        {
            navigate("/sign-in");
        }
    };

    const redirectToSessionExpired = () =>
    {
        if (!["/app-session-expired"].includes(location.pathname))
        {
            console.log("Now navigating to /app-session-expired");
            navigate("/app-session-expired");
        }
    }

    return (
        <AuthContext.Provider
            value={{
                token,
                setToken,
                appUser,
                setAppUser,
                appUserPersonOrganization,
                setAppUserPersonOrganization
            }}>
            {children}
        </AuthContext.Provider>
    );
};

export const useAuth = (): AuthContextType =>
{
    const context = useContext(AuthContext);

    if (!context)
    {
        throw new Error('useAuth must be used within an AuthProvider');
    }
    return context;
};