import React, {useEffect, useState} from "react";
import {useAuth} from "../../context/AuthContext.tsx";
import {isTokenExpired} from "../../utils/helpers.ts";
import {useNavigate, useSearchParams} from "react-router-dom";
import {Button, MessageBar, MessageBarBody, Subtitle1, Text} from "@fluentui/react-components";
import {useAppSessionExpiredStyles} from "./AppSessionExpiredStyles.tsx";
import {useAuthorizationStyles} from "../authorization/AuthorizationStyles.tsx";
import SignInCarousel from "../authorization/carousel/SignInCarousel.tsx";
import AppLogo from "../components/app-logo/AppLogo.tsx";

const REASON_MESSAGES: Record<string, string> = {
    SECURITY_SIGN_OUT: "Your session was ended for security reasons. Please sign in again.",
    ACCOUNT_DEPROVISIONED: "Your account has been deprovisioned. Contact your administrator for help.",
    ORG_MEMBERSHIP_INACTIVE: "Your organization membership is no longer active.",
    EXCHANGE_VERSION_MISMATCH: "Your session was invalidated because you signed out from all devices.",
    REFRESH_REUSE_DETECTED: "A suspicious sign-in attempt was detected and your session was terminated for your protection.",
    INACTIVITY_TIMEOUT: "You were signed out because your session was inactive.",
};

const AppSessionExpired: React.FC = () =>
{
    const {token, setToken, setAccessToken, setIdToken, setAppUser, setAppUserPersonOrganization} = useAuth();
    const navigate = useNavigate();
    const [searchParams] = useSearchParams();
    const [message, setMessage] = useState<string>("Your session has expired. Please sign in again.");
    const authorizationStyles = useAuthorizationStyles();
    const styles = useAppSessionExpiredStyles();

    useEffect(() =>
    {
        const reason = searchParams.get('reason')?.toUpperCase() ?? '';
        if (reason && REASON_MESSAGES[reason])
        {
            setMessage(REASON_MESSAGES[reason]);
        }

        if (token != null && !isTokenExpired(token))
        {
            navigate("/exchanges");
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
        <div className={authorizationStyles.auth}>
            <section className={authorizationStyles.authSection}>
                <section className={authorizationStyles.authSection1}>
                    <div>
                        <AppLogo/>
                    </div>
                    <div className={authorizationStyles.authorizationFormSection}>
                        <Subtitle1 align="center">Session Ended</Subtitle1>
                        <MessageBar intent="warning">
                            <MessageBarBody>{message}</MessageBarBody>
                        </MessageBar>
                        <Text size={300} className={styles.mutedText}>
                            For your security, sign in again to continue.
                        </Text>
                        <Button id={"app-session-expired-sign-in-btn"}
                                appearance="primary"
                                shape="circular"
                                onClick={handleSignIn}>
                            Sign In
                        </Button>
                    </div>
                </section>
                <section className={authorizationStyles.authSection2}>
                    <SignInCarousel/>
                </section>
            </section>
        </div>
    );
};

export default AppSessionExpired;
