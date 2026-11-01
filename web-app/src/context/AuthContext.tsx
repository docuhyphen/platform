import React, {createContext, ReactNode, useCallback, useContext} from 'react';
import {useLocation, useNavigate} from "react-router-dom";
import {setApiClientActiveOrganizationId} from "../services/apiClient.ts";
import OrganizationPickerDialog from "../app/components/organization-picker/OrganizationPickerDialog.tsx";
import SessionInactivityGuard from "../app/components/session-expiry-warning/SessionInactivityGuard.tsx";
import {AuthContextType} from "./auth/authContextTypes.ts";
import {AUTH_ACTIVE_ORG_STORAGE_KEY, AUTH_PERSONAL_MODE_STORAGE_KEY} from "./auth/authStorageKeys.ts";
import {useAuthTokens} from "./auth/useAuthTokens.ts";
import {useAuthSessionState} from "./auth/useAuthSessionState.ts";
import {useAuthBroadcast} from "./auth/useAuthBroadcast.ts";
import {useAuthWindowEvents} from "./auth/useAuthWindowEvents.ts";
import {useAuthStorageSync} from "./auth/useAuthStorageSync.ts";
import {useAuthUserData} from "./auth/useAuthUserData.ts";

export {resolveOrgSelection} from "./auth/orgSelection.ts";
export type {OrgSelectionResolution} from "./auth/orgSelection.ts";

const AuthContext = createContext<AuthContextType | undefined>(undefined);

export const AuthProvider: React.FC<{ children: ReactNode }> = ({children}) =>
{
    const navigate = useNavigate();
    const location = useLocation();
    const tokens = useAuthTokens();
    const session = useAuthSessionState();
    const {accessToken, idToken, token, isBootstrapping, setAccessToken, setIdToken, setToken, refreshTokens} = tokens;
    const {
        appUser,
        setAppUser,
        appUserPersonOrganization,
        setAppUserPersonOrganization,
        currentSession,
        orgPickerOptions,
        clearSession,
        hasCapability,
        refreshCurrentSession,
        switchOrganization,
        onOrgPickerSelect,
    } = session;

    const clearAuthStateAndRedirect = useCallback((path: string = '/sign-in') =>
    {
        setAccessToken(null);
        setIdToken(null);
        clearSession();
        localStorage.removeItem(AUTH_ACTIVE_ORG_STORAGE_KEY);
        localStorage.removeItem(AUTH_PERSONAL_MODE_STORAGE_KEY);
        setApiClientActiveOrganizationId(null);
        navigate(path);
    }, [setAccessToken, setIdToken, clearSession, navigate]);

    const {tabIdRef, sessionEndReasonRef, broadcastAuthEvent, suppressNextLogoutBroadcast} = useAuthBroadcast({
        accessToken,
        appUserId: appUser?.id,
    });

    useAuthWindowEvents({
        setAccessToken,
        setIdToken,
        clearAuthStateAndRedirect,
        broadcastAuthEvent,
        suppressNextLogoutBroadcast,
        sessionEndReasonRef,
    });

    useAuthStorageSync({
        appUserId: appUser?.id,
        tabIdRef,
        suppressNextLogoutBroadcast,
        clearAuthStateAndRedirect,
    });

    useAuthUserData({
        accessToken,
        setAccessToken,
        setIdToken,
        session,
        navigate,
        pathname: location.pathname,
    });

    return (
        <AuthContext.Provider
            value={{
                token,
                setToken,
                accessToken,
                idToken,
                setAccessToken,
                setIdToken,
                appUser,
                setAppUser,
                appUserPersonOrganization,
                setAppUserPersonOrganization,
                currentSession,
                hasCapability,
                refreshCurrentSession,
                switchOrganization,
                refreshTokens,
                isBootstrapping,
            }}>
            {children}
            <OrganizationPickerDialog isOpen={orgPickerOptions !== null}
                                      organizations={orgPickerOptions ?? []}
                                      onSelect={onOrgPickerSelect}/>
            <SessionInactivityGuard token={token}
                                    currentSession={currentSession}
                                    setToken={setToken}/>
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
