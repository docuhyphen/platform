import React, {KeyboardEvent} from 'react';
import {Button, Subtitle1} from "@fluentui/react-components";
import {ArrowLeftRegular} from "@fluentui/react-icons";
import RedirectIfAuthenticated from '../../components/RedirectIfAuthenticated.tsx';
import AppLogo from "../../components/app-logo/AppLogo.tsx";
import SignInCarousel from "../carousel/SignInCarousel.tsx";
import {useAuthorizationStyles} from "../AuthorizationStyles.tsx";
import {useGlobalStyles} from "../../../GlobalStyles.tsx";
import {useSignInFlow} from "./useSignInFlow.ts";
import SignInErrorMessage from "./error-message/SignInErrorMessage.tsx";
import SignInEmailStep from "./email-step/SignInEmailStep.tsx";
import SignInPasswordStep from "./password-step/SignInPasswordStep.tsx";
import SignInMfaStep from "./mfa-step/SignInMfaStep.tsx";
import SignInAccountLinks from "./account-links/SignInAccountLinks.tsx";

const SignIn: React.FC = () =>
{
    const flow = useSignInFlow();
    const authorizationStyles = useAuthorizationStyles();
    const globalStyles = useGlobalStyles();

    const onCodeKeyDown = (event: KeyboardEvent<HTMLInputElement>) =>
    {
        if (event.key === 'Enter')
        {
            void flow.onCompleteSignIn();
        }
    };

    return (
        <RedirectIfAuthenticated element={
            <section
                id={"sign-in-auth"}
                className={authorizationStyles.auth}>
                <section
                    id={"sign-in-auth-section"}
                    className={authorizationStyles.authSection}>
                    <section
                        id={"sign-in-auth-section-form"}
                        className={authorizationStyles.authSection1}>
                        <div id={"sign-in-auth-logo"}>
                            <AppLogo/>
                        </div>
                        <div
                            id={"sign-in-auth-form"}
                            className={authorizationStyles.authorizationFormSection}>
                            <Subtitle1
                                id={"sign-in-title"}
                                align={"center"}>
                                {flow.step !== 'EMAIL_ENTRY' && <Button
                                    id={"sign-in-back-btn"}
                                    shape={"circular"}
                                    icon={<ArrowLeftRegular/>}
                                    appearance={"transparent"}
                                    onClick={flow.onResetSignIn}
                                />}
                                Sign in
                            </Subtitle1>
                            <SignInErrorMessage
                                message={flow.responseErrorMessage}
                                onDismiss={flow.onDismissError}
                            />
                            {flow.step === 'EMAIL_ENTRY' && <SignInEmailStep
                                email={flow.email}
                                lookingUp={flow.lookingUp}
                                buttonWithLoadingClassName={globalStyles.buttonWithLoading}
                                onEmailChange={flow.onEmailChange}
                                onContinue={() => void flow.onLookupEmail()}
                            />}
                            {flow.step === 'PASSWORD_ENTRY' && <SignInPasswordStep
                                email={flow.email}
                                password={flow.password}
                                signingIn={flow.signInInitiating}
                                buttonWithLoadingClassName={globalStyles.buttonWithLoading}
                                onPasswordChange={flow.onPasswordChange}
                                onSubmit={() => void flow.onInitiateSignIn()}
                            />}
                            {flow.step === 'MFA_ENTRY' && <SignInMfaStep
                                message={flow.signInInitiationSuccessfulMsg}
                                method={flow.mfaMethod}
                                emailFallbackEnabled={flow.emailFallbackEnabled}
                                code={flow.otp}
                                successMessage={flow.resetOtpResponseMessage}
                                sessionExpired={flow.sessionExpired}
                                busy={flow.signInCompleting}
                                resending={flow.resendingOtp}
                                resendCooldownRemaining={flow.resendCooldownRemaining}
                                buttonWithLoadingClassName={globalStyles.buttonWithLoading}
                                onCodeChange={flow.onOtpChange}
                                onCodeKeyDown={onCodeKeyDown}
                                onResend={() => void flow.onResendOtp()}
                                onUseEmailFallback={() => void flow.onUseEmailFallback()}
                                onVerify={() => void flow.onCompleteSignIn()}
                                onStartOver={flow.onResetSignIn}
                            />}
                            <SignInAccountLinks disabled={flow.signInInitiating || flow.signInCompleting}/>
                        </div>
                    </section>
                    <section
                        id={"sign-in-auth-section-carousel"}
                        className={authorizationStyles.authSection2}>
                        <SignInCarousel/>
                    </section>
                </section>
            </section>
        }/>
    );
};

export default SignIn;
