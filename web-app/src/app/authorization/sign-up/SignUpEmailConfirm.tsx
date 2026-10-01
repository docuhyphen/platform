import React from 'react';
import AppLogo from "../../components/app-logo/AppLogo.tsx";
import SignUpCarousel from "../carousel/SignUpCarousel.tsx";
import {useAuthorizationStyles} from "../AuthorizationStyles.tsx";
import {useSignUpEmailConfirm} from "./email-confirm/useSignUpEmailConfirm.ts";
import EmailConfirmValidating from "./email-confirm/EmailConfirmValidating.tsx";
import EmailConfirmInvalidLink from "./email-confirm/EmailConfirmInvalidLink.tsx";
import EmailConfirmForm from "./email-confirm/EmailConfirmForm.tsx";
import EmailConfirmSuccess from "./email-confirm/EmailConfirmSuccess.tsx";

const SignUpEmailConfirm: React.FC = () =>
{
    const authorizationStyles = useAuthorizationStyles();
    const confirmation = useSignUpEmailConfirm();

    const renderBody = () =>
    {
        if (confirmation.signUpSuccessful) return <EmailConfirmSuccess/>;
        if (confirmation.validatingToken) return <EmailConfirmValidating/>;
        if (confirmation.tokenError || !confirmation.email) return <EmailConfirmInvalidLink tokenError={confirmation.tokenError}/>;
        return <EmailConfirmForm confirmation={confirmation}/>;
    };

    return (
        <section
            id={"email-confirm-auth"}
            className={authorizationStyles.auth}>
            <section
                id={"email-confirm-auth-section"}
                className={authorizationStyles.authSection}>
                <section
                    id={"email-confirm-auth-section-form"}
                    className={authorizationStyles.authSection1}>
                    <div id={"email-confirm-auth-logo"}>
                        <AppLogo/>
                    </div>
                    {renderBody()}
                </section>
                <section
                    id={"email-confirm-auth-section-carousel"}
                    className={authorizationStyles.authSection2}>
                    <SignUpCarousel/>
                </section>
            </section>
        </section>
    );
};

export default SignUpEmailConfirm;
