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
        console.log("Token in storage ", token);

        const fetchUserDetails = async () =>
        {
            if (token && !isTokenExpired(token))
            {
                if (!appUser)
                {
                    const user = await fetchAppUser(token);
                    setAppUser(user);
                }

                if (appUser && !appUser?.person)
                {
                    alert("No Person, Navigating to individual registration");
                    navigate('/onboarding/individual-registration');
                    return;
                }

                if (!appUserPersonCompany && location.pathname === '/onboarding/company-registration')
                {
                    return
                }

                try
                {
                    const company = await fetchAppUserPersonCompany(appUser?.id, appUser?.person?.id, token);
                    setAppUserPersonCompany(company);

                    if (company && company.verificationComplete)
                    {
                        navigate('/landing');
                    }
                    else
                    {
                        // navigate('/onboarding/company-registration-pending');
                    }
                }
                catch (e)
                {
                    console.log("!!!");
                    console.log(e);
                    navigate('/landing');
                }

            }
            else
            {
                saveToken(null);
            }
        };

        fetchUserDetails();

        const intervalId = setInterval(() =>
        {
            if (token && isTokenExpired(token))
            {
                saveToken(null);
            }
        }, 30000);

        return () => clearInterval(intervalId);
    }, [token, navigate, location.pathname]);

    // useEffect(() =>
    // {
    //     if (appUser && !appUser.person)
    //     {
    //         navigate('/onboarding/individual-registration');
    //     }
    // }, [appUser, navigate]);

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