import React, {useEffect} from "react";
import {useAuth} from "../../context/AuthContext.tsx";
import {isTokenExpired} from "../../utils/helpers.ts";
import {useNavigate, useSearchParams} from "react-router-dom";
import {Button, MessageBar, MessageBarBody, Subtitle1, Text} from "@fluentui/react-components";
import {useAppSessionExpiredStyles} from "./AppSessionExpiredStyles.tsx";
import {useAuthorizationStyles} from "../authorization/AuthorizationStyles.tsx";
import SignInCarousel from "../authorization/carousel/SignInCarousel.tsx";
import AppLogo from "../components/app-logo/AppLogo.tsx";
import {getSessionEndMessage} from "./sessionEndMessages.ts";

const AppSessionExpired: React.FC = () =>
{
    const {token, setToken, setAccessToken, setIdToken, setAppUser, setAppUserPersonOrganization} = useAuth();
    const navigate = useNavigate();
    const [searchParams] = useSearchParams();
    const message = getSessionEndMessage(searchParams.get('reason'));
    const authorizationStyles = useAuthorizationStyles();
    const styles = useAppSessionExpiredStyles();

    useEffect(() =>
    {
        if (token != null && !isTokenExpired(token))
        {
            navigate("/exchanges");
        }
    }, [navigate, token]);

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
        <div id={"app-session-expired-div-1"}
             className={authorizationStyles.auth}>
            <section id={"app-session-expired-section-1"}
                     className={authorizationStyles.authSection}>
                <section id={"app-session-expired-section-2"}
                         className={authorizationStyles.authSection1}>
                    <div id={"app-session-expired-div-2"}>
                        <AppLogo/>
                    </div>
                    <div id={"app-session-expired-div-3"}
                         className={authorizationStyles.authorizationFormSection}>
                        <Subtitle1 id={"app-session-expired-subtitle1-1"}
                                   align="center">Session Ended</Subtitle1>
                        <MessageBar id={"app-session-expired-message-bar-1"}
                                    intent="warning">
                            <MessageBarBody id={"app-session-expired-message-bar-body-1"}>{message}</MessageBarBody>
                        </MessageBar>
                        <Text id={"app-session-expired-text-1"}
                              size={300}
                              className={styles.mutedText}>
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
                <section id={"app-session-expired-section-3"}
                         className={authorizationStyles.authSection2}>
                    <SignInCarousel/>
                </section>
            </section>
        </div>
    );
};

export default AppSessionExpired;
