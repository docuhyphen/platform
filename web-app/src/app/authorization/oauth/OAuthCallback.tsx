import React, {useEffect, useState} from 'react';
import {useNavigate, useSearchParams} from 'react-router-dom';
import {useAuth} from '../../../context/AuthContext.tsx';
import {setApiClientAuthToken} from '../../../services/apiClient.ts';
import {fetchAppUser, fetchAppUserPersonOrganization} from '../../../services/appUserApi.ts';
import {Button, MessageBar, MessageBarBody, Spinner, Text} from "@fluentui/react-components";
import {useOAuthStyles} from "./OAuthStyles.tsx";

const ERROR_MESSAGES: Record<string, string> = {
    ACCOUNT_DEPROVISIONED: "Your account has been deprovisioned. Please contact your administrator.",
    ORG_MEMBERSHIP_INACTIVE: "Your organization membership is no longer active.",
    USER_CAP_EXCEEDED: "Your organization has reached its user limit. Please contact your administrator.",
    CSRF_VALIDATION_FAILED: "Security validation failed. Please try signing in again.",
    OIDC_VALIDATION_FAILED: "Identity verification failed. Please try again or contact support.",
    OAUTH_STATE_INVALID: "Sign-in session expired or was tampered with. Please try again.",
    SECURITY_SIGN_OUT: "You were signed out for security reasons. Please sign in again.",
};

const OAuthCallback: React.FC = () =>
{
    const [searchParams] = useSearchParams();
    const {setAccessToken, setIdToken, setAppUser, setAppUserPersonOrganization} = useAuth();
    const navigate = useNavigate();
    const [errorMessage, setErrorMessage] = useState<string | null>(null);
    const styles = useOAuthStyles();

    useEffect(() =>
    {
        const processCallback = async () =>
        {
            const error = searchParams.get('error');
            const errorCode = searchParams.get('errorCode');

            if (error || errorCode)
            {
                const code = errorCode || error || '';
                const message = ERROR_MESSAGES[code.toUpperCase()] ?? `Sign-in failed: ${error ?? 'Unknown error'}. Please try again.`;
                setErrorMessage(message);
                return;
            }

            const accessToken = searchParams.get('accessToken');
            const idToken = searchParams.get('idToken');
            const isNewUser = searchParams.get('isNewUser') === 'true';

            if (!accessToken)
            {
                setErrorMessage("OAuth sign-in did not complete. Please try again.");
                return;
            }

            setAccessToken(accessToken);
            if (idToken) setIdToken(idToken);
            setApiClientAuthToken(accessToken);

            if (isNewUser)
            {
                navigate('/onboarding/individual');
                return;
            }

            try
            {
                const appUser = await fetchAppUser(accessToken);
                setAppUser(appUser);

                if (appUser && appUser.person)
                {
                    try
                    {
                        const org = await fetchAppUserPersonOrganization(appUser.id, appUser.person?.id, accessToken);
                        setAppUserPersonOrganization(org);
                    }
                    catch
                    {
                        // Organization not found is okay
                    }
                    navigate('/exchanges');
                }
                else
                {
                    navigate('/onboarding/individual');
                }
            }
            catch
            {
                navigate('/onboarding/individual');
            }
        };

        processCallback();
    }, []);

    if (errorMessage)
    {
        return (
            <div className={styles.oauthCallbackContainer}>
                <MessageBar
                    intent="error"
                    className={styles.oauthMessageBar}>
                    <MessageBarBody>{errorMessage}</MessageBarBody>
                </MessageBar>
                <Button
                    id={"oauth-callback-back-to-sign-in-btn"}
                    appearance="primary"
                    shape={"circular"}
                    onClick={() => navigate('/sign-in')}>
                    Back to Sign In
                </Button>
            </div>
        );
    }

    return (
        <div className={styles.oauthSpinnerContainer}>
            <Spinner size="large"/>
            <Text size={400}>Completing sign-in...</Text>
        </div>
    );
};

export default OAuthCallback;
