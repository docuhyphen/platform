import React from 'react';
import AppLogo from "../../components/app-logo/AppLogo.tsx";
import SignUpCarousel from "../carousel/SignUpCarousel.tsx";
import {useAuthorizationStyles} from "../AuthorizationStyles.tsx";
import {useSignUp} from "./useSignUp.ts";
import SignUpForm from "./sign-up-form/SignUpForm.tsx";
import SignUpSuccess from "./sign-up-success/SignUpSuccess.tsx";

const SignUp: React.FC = () =>
{
    const authorizationStyles = useAuthorizationStyles();
    const signUp = useSignUp();

    return (
        <section id={"sign-up-section-1"}
                 className={authorizationStyles.auth}>
            <section id={"sign-up-section-2"}
                     className={authorizationStyles.authSection}>
                <section id={"sign-up-section-3"}
                         className={authorizationStyles.authSection1}>
                    <div id={"sign-up-div-1"}>
                        <AppLogo/>
                    </div>
                    {!signUp.signUpSuccessful && <SignUpForm signUp={signUp}/>}
                    {signUp.signUpSuccessful && <SignUpSuccess/>}
                </section>
                <section id={"sign-up-section-4"}
                         className={authorizationStyles.authSection2}>
                    <SignUpCarousel/>
                </section>
            </section>
        </section>
    );
};

export default SignUp;
