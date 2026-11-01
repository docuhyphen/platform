import React, {useState} from 'react';
import {useNavigate, useSearchParams} from 'react-router-dom';
import {useAuth} from '../../../context/AuthContext.tsx';
import {setApiClientAuthToken} from '../../../services/apiClient.ts';
import {confirmOAuthLink} from '../../../services/authApi.ts';
import {
    Button,
    Field,
    Input,
    InputOnChangeData,
    MessageBar,
    MessageBarBody,
    Spinner,
    Subtitle1,
    Text,
} from "@fluentui/react-components";
import {ResponseError} from "../../models/models.tsx";
import AppLogo from "../../components/app-logo/AppLogo.tsx";
import {useAuthorizationStyles} from "../AuthorizationStyles.tsx";
import {useGlobalStyles} from "../../../GlobalStyles.tsx";
import {identityProviderDisplayName} from "../identityProviderDisplayName.ts";

const OAuthLinkConfirm: React.FC = () =>
{
    const [searchParams] = useSearchParams();
    const {setAccessToken, setIdToken} = useAuth();
    const navigate = useNavigate();
    const authorizationStyles = useAuthorizationStyles();
    const globalStyles = useGlobalStyles();

    const provider = searchParams.get('provider') || '';
    const providerDisplayName = identityProviderDisplayName(provider);
    const email = searchParams.get('email') || '';
    const linkToken = searchParams.get('linkToken') || '';

    const [password, setPassword] = useState('');
    const [loading, setLoading] = useState(false);
    const [errorMessage, setErrorMessage] = useState<string | undefined>();

    const onPasswordChange = (_e: React.ChangeEvent<HTMLInputElement>, newValue: InputOnChangeData) =>
    {
        setPassword(newValue.value || '');
    };

    const onConfirm = async () =>
    {
        if (!password)
        {
            setErrorMessage("Password is required.");
            return;
        }

        setLoading(true);
        setErrorMessage(undefined);

        try
        {
            const response = await confirmOAuthLink({linkToken, password});

            setAccessToken(response.accessToken);
            if (response.idToken) setIdToken(response.idToken);
            setApiClientAuthToken(response.accessToken);

            navigate('/exchanges');
        }
        catch (error)
        {
            setErrorMessage((error as ResponseError)?.errorMessage || "Failed to link account.");
        }
        finally
        {
            setLoading(false);
        }
    };

    const handleKeyDown = (event: React.KeyboardEvent<HTMLInputElement>) =>
    {
        if (event.key === 'Enter') onConfirm();
    };

    return (
        <section id={"o-auth-link-confirm-section-1"}
                 className={authorizationStyles.auth}>
            <section id={"o-auth-link-confirm-section-2"}
                     className={authorizationStyles.authSection}>
                <section id={"o-auth-link-confirm-section-3"}
                         className={authorizationStyles.authSection1}>
                    <div id={"o-auth-link-confirm-div-1"}>
                        <AppLogo/>
                    </div>
                    <div id={"o-auth-link-confirm-div-2"}
                         className={authorizationStyles.authorizationFormSection}>
                        <Subtitle1 id={"o-auth-link-confirm-subtitle1-1"}
                                   align={"center"}>Link Account</Subtitle1>

                        <Text id={"o-auth-link-confirm-text-1"}
                              size={300}
                              align={"center"}>
                            An account with <strong id={"o-auth-link-confirm-strong-1"}>{email}</strong> already exists.
                            Enter your password to link your <strong id={"o-auth-link-confirm-strong-2"}>{providerDisplayName}</strong> account.
                        </Text>

                        {errorMessage && (
                            <MessageBar id={"o-auth-link-confirm-message-bar-1"}
                                        intent={"error"}>
                                <MessageBarBody id={"o-auth-link-confirm-message-bar-body-1"}>{errorMessage}</MessageBarBody>
                            </MessageBar>
                        )}

                        <Field id={"o-auth-link-confirm-field-1"}
                               label={"Password"}>
                            <Input
                                id={"oauth-link-password-input"}
                                type="password"
                                value={password}
                                onChange={onPasswordChange}
                                onKeyDown={handleKeyDown}/>
                        </Field>

                        <Button
                            id={"oauth-link-confirm-btn"}
                            onClick={onConfirm}
                            appearance="primary"
                            shape={"circular"}
                            className={globalStyles.buttonWithLoading}
                            disabled={loading}>
                            {loading && <Spinner id={"o-auth-link-confirm-spinner-1"}
                                                 size={"tiny"}/>}
                            {loading ? "Linking..." : "Link Account"}
                        </Button>

                        <Button
                            id={"oauth-link-cancel-btn"}
                            onClick={() => navigate('/sign-in')}
                            appearance="transparent"
                            shape={"circular"}>
                            Cancel
                        </Button>
                    </div>
                </section>
            </section>
        </section>
    );
};

export default OAuthLinkConfirm;

