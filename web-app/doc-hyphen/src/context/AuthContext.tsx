import React, {createContext, ReactNode, useContext, useEffect, useRef, useState} from 'react';
import {fetchAppUser, fetchAppUserPersonCompany} from '../services/userApi.ts';
import {AppUserDetailedDto, Company} from "../app/models/models.tsx";
import {isTokenExpired} from "../utils/helpers.ts";
import {useLocation, useNavigate} from "react-router-dom";

interface AuthContextType
{
    token: string | null;
    setToken: (token: string | null) => void;
    appUser: AppUserDetailedDto | null;
    setAppUser: (user: AppUserDetailedDto | null) => void;
    appUserPersonCompany: Company | null;
    setAppUserPersonCompany: (company: Company | null) => void;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

export const AuthProvider: React.FC<{ children: ReactNode }> = ({children}) =>
{
    const navigate = useNavigate();
    const location = useLocation();
    const [token, setToken] = useState<string | null>(() => localStorage.getItem('token'));
    const tokenExpirationIntervalRef = useRef<NodeJS.Timeout | null>(null);
    const [appUser, setAppUser] = useState<AppUserDetailedDto | null>(null);
    const [appUserPersonCompany, setAppUserPersonCompany] = useState<Company | null>(null);

    useEffect(() =>
    {
        if (!tokenExpirationIntervalRef.current)
        {
            console.log("Running token expiration check");

            tokenExpirationIntervalRef.current = setInterval(() =>
            {
                setToken((currentToken) =>
                {
                    if (currentToken && isTokenExpired(currentToken))
                    {
                        saveToken(null);
                        return null;
                    }
                    return currentToken;
                });
            }, 30000);
        }

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
        }
        else
        {
            localStorage.setItem('token', token);
        }
    }, [token])

    useEffect(() =>
    {
        if (!token || isTokenExpired(token))
        {
            saveToken(null);
            redirectToLogin();
            return;
        }

        fetchUserData();
    }, [token, location.pathname]);

    const fetchUserData = async () =>
    {
        try
        {
            if (!appUser)
            {
                const user = await fetchAppUser(token!);
                setAppUser(user);
            }

            // If user has no `person` object, redirect to individual onboarding
            if (appUser && !appUser.person)
            {
                navigate("/onboarding/individual-registration");
                return;
            }

            if (appUser?.person && !appUserPersonCompany)
            {
                try
                {
                    setAppUserPersonCompany(await fetchAppUserPersonCompany(appUser?.id, appUser?.person?.id, token!));
                }
                catch (error)
                {
                    if (error.response.status === 404)
                    {
                        console.log("Company not found for user");
                    }
                }
            }

            // If everything exists, navigate to the main page
            if (appUser && appUser.person && appUserPersonCompany)
            {
                navigate("/sharing-sessions");
            }
        }
        catch (error)
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
        }
    };

    const redirectToLogin = () =>
    {
        if (!["/sign-in", "/sign-up", "/account-recovery", "/nas"].includes(location.pathname))
        {
            navigate("/sign-in");
        }
    };

    useEffect(() =>
    {
        console.log("AppUser in AuthContext", appUser);

        if (appUser)
        {
            if (!appUser.person)
            {
                navigate('/onboarding/individual-registration');
            }
        }
    }, [appUser, navigate]);

    return (
        <AuthContext.Provider
            value={{token, setToken, appUser, setAppUser, appUserPersonCompany, setAppUserPersonCompany}}>
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