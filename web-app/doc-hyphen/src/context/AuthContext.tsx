import React, {createContext, ReactNode, useContext, useEffect, useState} from 'react';
import {fetchAppUser, fetchAppUserPersonCompany} from '../services/api';
import {AppUser, Company} from "../app/models/models.tsx";
import {isTokenExpired} from "../utils/helpers.ts";
import {useLocation, useNavigate} from "react-router-dom";

interface AuthContextType
{
    token: string | null;
    setToken: (token: string | null) => void;
    appUser: AppUser | null;
    setAppUser: (user: AppUser | null) => void;
    appUserPersonCompany: Company | null;
    setAppUserPersonCompany: (company: Company | null) => void;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

export const AuthProvider: React.FC<{ children: ReactNode }> = ({children}) =>
{
    const [token, setToken] = useState<string | null>(() => localStorage.getItem('token'));
    const [appUser, setAppUser] = useState<AppUser | null>(null);
    const [appUserPersonCompany, setAppUserPersonCompany] = useState<Company | null>(null);

    const navigate = useNavigate();
    const location = useLocation();

    const saveToken = (newToken: string | null) =>
    {
        setToken(newToken);

        if (newToken)
        {
            localStorage.setItem('token', newToken);
        }
        else
        {
            localStorage.removeItem('token');
            setAppUser(null);
        }
    };

    useEffect(() =>
    {
        console.log("Current page is ", location.pathname);
        console.log("Token in storage ", token);

        const fetchAppUserDetails = async () =>
        {
            if (token && !isTokenExpired(token))
            {
                console.log("Token is not expired");

                let user: AppUser | null = null

                try
                {
                    user = await fetchAppUser(token);
                    setAppUser(user);
                }
                catch (e)
                {

                }

                if (user && !appUserPersonCompany)
                {
                    try
                    {
                        console.log("There's no company, fetching company");
                        const company = await fetchAppUserPersonCompany(user?.id, user?.person?.id, token);
                        setAppUserPersonCompany(company);
                    }
                    catch (e)
                    {

                    }
                }
            }
            else
            {
                saveToken(null)

                if (location.pathname !== "/sign-up" &&
                    location.pathname !== "/sign-in" &&
                    location.pathname !== "/account-recovery" &&
                    location.pathname !== "/not-found")
                {
                    navigate("/sign-in")
                }
            }
        };

        fetchAppUserDetails();

        const intervalId = setInterval(() =>
        {
            if (token && isTokenExpired(token))
            {
                saveToken(null);
            }
        }, 30000);

        return () => clearInterval(intervalId);
    }, [token, navigate, location.pathname]);

    useEffect(() =>
    {
        if (appUser && !appUser.person)
        {
            navigate('/onboarding/individual-registration');
        }
    }, [appUser, navigate]);

    return (
        <AuthContext.Provider
            value={{
                token, setToken: saveToken,
                appUser,
                setAppUser,
                appUserPersonCompany,
                setAppUserPersonCompany
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