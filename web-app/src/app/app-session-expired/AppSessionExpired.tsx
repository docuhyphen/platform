import React, {useEffect, useState} from "react";
import {useAuth} from "../../context/AuthContext.tsx";
import {isTokenExpired} from "../../utils/helpers.ts";
import {useNavigate, useSearchParams} from "react-router-dom";
import {Button, MessageBar, MessageBarBody, Text, Title2} from "@fluentui/react-components";

const REASON_MESSAGES: Record<string, string> = {
    SECURITY_SIGN_OUT: "Your session was ended for security reasons. Please sign in again.",
    ACCOUNT_DEPROVISIONED: "Your account has been deprovisioned. Contact your administrator for help.",
    ORG_MEMBERSHIP_INACTIVE: "Your organization membership is no longer active.",
    SESSION_VERSION_MISMATCH: "Your session was invalidated because you signed out from all devices.",
    REFRESH_REUSE_DETECTED: "A suspicious sign-in attempt was detected and your session was terminated for your protection.",
};

const AppSessionExpired: React.FC = () =>
{
    const {token, setToken, setAccessToken, setIdToken, setAppUser, setAppUserPersonOrganization} = useAuth();
    const navigate = useNavigate();
    const [searchParams] = useSearchParams();
    const [message, setMessage] = useState<string>("Your session has expired. Please sign in again.");

    useEffect(() =>
    {
        const reason = searchParams.get('reason')?.toUpperCase() ?? '';
        if (reason && REASON_MESSAGES[reason])
        {
            setMessage(REASON_MESSAGES[reason]);
        }

        if (token != null && !isTokenExpired(token))
        {
            navigate("/sharing-sessions");
        }
    }, []);

    const handleSignIn = () =>
    {
        setToken(null);
        setAccessToken(null);
        setIdToken(null);
        setAppUser(null);
        setAppUserPersonOrganization(null);
        navigate("/sign-in");
    };

    return (
        <div style={{display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', height: '100vh', gap: '20px', padding: '32px', maxWidth: '480px', margin: '0 auto'}}>
            <Title2>Session Ended</Title2>
            <MessageBar intent="warning" style={{width: '100%'}}>
                <MessageBarBody>{message}</MessageBarBody>
            </MessageBar>
            <Text size={300} style={{color: '#666', textAlign: 'center'}}>
                For your security, sign in again to continue.
            </Text>
            <Button appearance="primary" shape="circular" onClick={handleSignIn}>
                Sign In
            </Button>
        </div>
    );
};

export default AppSessionExpired;
