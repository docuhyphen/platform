import React, {ChangeEvent, useEffect, useMemo, useState} from 'react';
import {useNavigate, useSearchParams} from 'react-router-dom';
import {checkSignUpEmailConfirmToken, confirmSignUpEmail, regenerateSignUpOtp} from "../../../services/authApi.ts";
import {
    Button,
    Caption1,
    Field,
    InfoLabel,
    Input,
    InputOnChangeData,
    Link,
    MessageBar,
    MessageBarActions,
    MessageBarBody,
    Spinner,
    Subtitle1,
    Text
} from "@fluentui/react-components";
import {ArrowLeftRegular, DismissRegular} from "@fluentui/react-icons";
import AppLogo from "../../components/app-logo/AppLogo.tsx";
import SignUpCarousel from "../carousel/SignUpCarousel.tsx";
import {useSignUpStyles} from "./SignUpStyles.tsx";
import {useAuthorizationStyles} from "../AuthorizationStyles.tsx";
import {useGlobalStyles} from "../../../GlobalStyles.tsx";
import {getOtpFriendlyMessage, normalizeApiError} from "../../../utils/apiErrorUtils.ts";

/**
 * Landing page for the verification link sent in the sign-up email.
 *
 * The email contains: {baseUrl}/sign-up/email-confirm?token=<opaque>
 *
 * The token is a 32-byte secure random value (server-issued, server-stored
 * in Redis, single-use via GETDEL). No PII is ever in the URL.
 *
 * Flow:
 *   1. On mount, GET /auth/sign-up/email-confirm/{token} to validate the
 *      token and pull back the associated email,  without consuming it.
 *   2. Show the email read-only as context. User enters password + confirm.
 *   3. POST /auth/sign-up/email-confirm {token, password, confirmationPassword}.
 *      Server consumes the token atomically and finalises sign-up.
 *
 * If the token is missing / invalid / expired at any step, the user is
 * offered "Send a new code" (regenerates a fresh email with a fresh token).
 */
const SignUpEmailConfirm: React.FC = () =>
{
    const navigate = useNavigate();
    const [searchParams] = useSearchParams();
    const signUpStyles = useSignUpStyles();
    const authorizationStyles = useAuthorizationStyles();
    const globalStyles = useGlobalStyles();

    const token = useMemo(() => (searchParams.get('token') || '').trim(), [searchParams]);

    const [validatingToken, setValidatingToken] = useState<boolean>(true);
    const [tokenError, setTokenError] = useState<string | undefined>();
    const [email, setEmail] = useState<string>('');

    const [password, setPassword] = useState('');
    const [confirmationPassword, setConfirmationPassword] = useState('');
    const [completingSignUp, setCompletingSignUp] = useState(false);
    const [signUpSuccessful, setSignUpSuccessful] = useState(false);

    const [regeneratingOtp, setRegeneratingOtp] = useState(false);
    const [otpRegenerationSuccessMsg, setOtpRegenerationSuccessMsg] = useState<string>();
    const [otpRegenerationFailedMsg, setOtpRegenerationFailedMsg] = useState<string>();

    const [errorMessage, setErrorMessage] = useState<string | undefined>();

    /* ---------------------------------------------------------------------
     * On mount: validate the token by calling the backend introspect endpoint.
     * Sets `email` for display if the token is good, or `tokenError` if not.
     * ------------------------------------------------------------------- */
    useEffect(() =>
    {
        let cancelled = false;

        const validate = async () =>
        {
            if (!token)
            {
                setTokenError("This verification link is missing required information.");
                setValidatingToken(false);
                return;
            }

            try
            {
                const response = await checkSignUpEmailConfirmToken(token);
                if (cancelled) return;
                setEmail(response.email);
            }
            catch (error)
            {
                if (cancelled) return;
                setTokenError(getOtpFriendlyMessage(normalizeApiError(error, "This verification link is invalid or has expired.")));
            }
            finally
            {
                if (!cancelled) setValidatingToken(false);
            }
        };

        validate();
        return () => { cancelled = true; };
    }, [token]);

    const onPasswordChange = (e: ChangeEvent<HTMLInputElement>, newValue: InputOnChangeData) =>
    {
        setErrorMessage(undefined);
        if (e.target.name === 'password')
        {
            setPassword(newValue.value || '');
        }
        else if (e.target.name === 'confirmationPassword')
        {
            setConfirmationPassword(newValue.value || '');
        }
    };

    const validateForm = (): boolean =>
    {
        if (!password)
        {
            setErrorMessage("Password is required");
            return false;
        }

        if (!confirmationPassword)
        {
            setErrorMessage("Password confirmation is required");
            return false;
        }

        if (password !== confirmationPassword)
        {
            setErrorMessage("Passwords do not match");
            return false;
        }

        return true;
    };

    const onCompleteSignUp = async () =>
    {
        if (!token || completingSignUp) return;
        if (!validateForm()) return;

        setErrorMessage(undefined);
        setCompletingSignUp(true);

        try
        {
            await confirmSignUpEmail({token, password, confirmationPassword});
            setSignUpSuccessful(true);
        }
        catch (error)
        {
            const message = getOtpFriendlyMessage(normalizeApiError(error, "We couldn't complete sign up. The verification link may have expired."));
            setErrorMessage(message);

            // The token has been consumed (success or fail) by the server side. If
            // the backend says the link is invalid, fall back to the "request a new
            // link" UI by promoting the message to a token-level error.
            if (/invalid|expired|link/i.test(message))
            {
                setTokenError(message);
            }
        }
        finally
        {
            setCompletingSignUp(false);
        }
    };

    const onResendCode = async () =>
    {
        if (!email || regeneratingOtp) return;

        setOtpRegenerationSuccessMsg(undefined);
        setOtpRegenerationFailedMsg(undefined);
        setErrorMessage(undefined);
        setRegeneratingOtp(true);

        try
        {
            const response = await regenerateSignUpOtp({email});
            setOtpRegenerationSuccessMsg(
                response?.message
                || "We've sent a new verification email. Open it and click the new link to continue."
            );
        }
        catch (error)
        {
            setOtpRegenerationFailedMsg(getOtpFriendlyMessage(normalizeApiError(error, "Failed to send a new verification email.")));
        }
        finally
        {
            setRegeneratingOtp(false);
        }
    };

    const handleKeyDown = (event: React.KeyboardEvent<HTMLInputElement>) =>
    {
        if (event.key === 'Enter') onCompleteSignUp();
    };

    const renderErrorBar = () => (
        errorMessage && (
            <MessageBar intent={"error"}>
                <MessageBarBody>
                    <Text size={200}>{errorMessage}</Text>
                </MessageBarBody>
                <MessageBarActions
                    containerAction={
                        <Button
                            id={"email-confirm-error-dismiss-btn"}
                            shape={"circular"}
                            onClick={() => setErrorMessage(undefined)}
                            appearance="transparent"
                            icon={<DismissRegular/>}
                        />
                    }
                />
            </MessageBar>
        )
    );

    const renderValidating = () => (
        <div className={authorizationStyles.authorizationFormSection}>
            <Subtitle1 align={"center"}>Checking your verification link</Subtitle1>
            <div className={signUpStyles.validatingSpinnerContainer}>
                <Spinner size={"medium"} label={"Validating..."}/>
            </div>
        </div>
    );

    const renderInvalidLink = () => (
        <div className={authorizationStyles.authorizationFormSection}>
            <Subtitle1 align={"center"}>
                <Button
                    id={"email-confirm-invalid-back-btn"}
                    shape={"circular"}
                    icon={<ArrowLeftRegular/>}
                    appearance={"transparent"}
                    onClick={() => navigate("/sign-up")}/>
                Verification link issue
            </Subtitle1>
            <MessageBar intent={"warning"}>
                <MessageBarBody>
                    <Text size={200}>
                        {tokenError || "This verification link is invalid or has expired."}
                        {" "}Please start sign-up again to receive a new email.
                    </Text>
                </MessageBarBody>
            </MessageBar>
            <Button
                id={"email-confirm-back-to-signup-btn"}
                onClick={() => navigate("/sign-up")}
                appearance={"primary"}
                shape={"circular"}>
                Back to sign up
            </Button>
        </div>
    );

    const renderConfirmForm = () => (
        <div className={authorizationStyles.authorizationFormSection}>
            <Subtitle1 align={"center"}>
                <Button
                    id={"email-confirm-form-back-btn"}
                    shape={"circular"}
                    icon={<ArrowLeftRegular/>}
                    appearance={"transparent"}
                    onClick={() => navigate("/sign-up")}/>
                Finish creating your account
            </Subtitle1>

            <Text size={300} align={"center"}>
                Verifying <strong>{email}</strong>. Choose a password to complete sign up.
            </Text>

            {renderErrorBar()}

            <div className={signUpStyles.signUpCompletionForm}>
                <Field
                    label={"Password"}
                    validationState={"none"}
                    validationMessage={""}>
                    <Input
                           id={"email-confirm-password-input"}
                           type="password"
                           name="password"
                           maxLength={30}
                           value={password}
                           disabled={completingSignUp || regeneratingOtp}
                           onChange={onPasswordChange}
                           onKeyDown={handleKeyDown}
                           contentAfter={
                               <InfoLabel info={<>
                                   <strong>Password requirements</strong>
                                   <ul>
                                       <li>Must be at least 12 characters long</li>
                                       <li>Must not exceed 128 characters</li>
                                       <li>Must contain at least one uppercase letter</li>
                                       <li>Must contain at least one lowercase letter</li>
                                       <li>Must contain at least one digit</li>
                                       <li>Must contain at least one special character</li>
                                   </ul>
                               </>}/>
                           }/>
                </Field>
                <Field
                    label={"Password Confirmation"}
                    validationState={"none"}
                    validationMessage={""}>
                    <Input
                           id={"email-confirm-confirm-password-input"}
                           type={"password"}
                           name="confirmationPassword"
                           maxLength={30}
                           value={confirmationPassword}
                           disabled={completingSignUp || regeneratingOtp}
                           onChange={onPasswordChange}
                           onKeyDown={handleKeyDown}/>
                </Field>

                <Button
                    id={"email-confirm-complete-btn"}
                    onClick={onCompleteSignUp}
                    appearance={"primary"}
                    shape={"circular"}
                    disabled={completingSignUp || regeneratingOtp}
                    className={globalStyles.buttonWithLoading}>
                    {completingSignUp && <Spinner size={"tiny"}/>}
                    {completingSignUp ? "Completing sign up" : "Complete sign up"}
                </Button>
            </div>

            <Field
                validationState={otpRegenerationFailedMsg
                    ? "error"
                    : (otpRegenerationSuccessMsg ? "success" : "none")}
                validationMessage={otpRegenerationFailedMsg || otpRegenerationSuccessMsg}>
                <Button
                    id={"email-confirm-resend-btn"}
                    onClick={onResendCode}
                    size={"small"}
                    disabled={completingSignUp || regeneratingOtp}
                    appearance={"transparent"}
                    shape={"circular"}
                    className={globalStyles.buttonWithLoading}>
                    {regeneratingOtp && <Spinner size={"tiny"}/>}
                    Send me a new verification link
                </Button>
            </Field>

            <div className={signUpStyles.authHasAccount}>
                <Caption1> Already have an account? &nbsp;
                    <Link onClick={() => navigate("/sign-in")}
                          disabled={completingSignUp}>
                        <Text weight="semibold">Sign in</Text>
                    </Link>
                </Caption1>
            </div>
        </div>
    );

    const renderSuccess = () => (
        <div className={signUpStyles.signUpSuccessfulSection}>
            <Text align={"center"}
                  size={500}
                  font="monospace">
                Sign up successful!
            </Text>
            <Text align={"center"}
                  size={300}>
                Welcome aboard, your account has been created successfully.
            </Text>
            <Text align={"center"}
                  italic>
                <strong> Your account is protected </strong>.
                Two-factor authentication (2FA) is enabled by default to enhance
                your account security. We also recommend keeping your password
                secure with a trusted password manager.
            </Text>
            <Button
                id={"email-confirm-sign-in-btn"}
                onClick={() => navigate("/sign-in")}
                appearance={"primary"}
                shape={"circular"}>
                Sign In
            </Button>
        </div>
    );

    const renderBody = () =>
    {
        if (signUpSuccessful) return renderSuccess();
        if (validatingToken) return renderValidating();
        if (tokenError || !email) return renderInvalidLink();
        return renderConfirmForm();
    };

    return (
        <section className={authorizationStyles.auth}>
            <section className={authorizationStyles.authSection}>
                <section className={authorizationStyles.authSection1}>
                    <div>
                        <AppLogo/>
                    </div>
                    {renderBody()}
                </section>
                <section className={authorizationStyles.authSection2}>
                    <SignUpCarousel/>
                </section>
            </section>
        </section>
    );
};

export default SignUpEmailConfirm;
