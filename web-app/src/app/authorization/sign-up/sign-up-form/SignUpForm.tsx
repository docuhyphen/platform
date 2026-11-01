import {KeyboardEvent} from "react";
import {Button, Caption1, Divider, Field, Input, Link, Spinner, Subtitle1, Text} from "@fluentui/react-components";
import {ArrowLeftRegular} from "@fluentui/react-icons";
import {useNavigate} from "react-router-dom";
import {useSignUpStyles} from "../SignUpStyles.tsx";
import {useAuthorizationStyles} from "../../AuthorizationStyles.tsx";
import {useGlobalStyles} from "../../../../GlobalStyles.tsx";
import {SignUpState} from "../useSignUp.ts";
import SignUpErrorMessage from "../sign-up-error-message/SignUpErrorMessage.tsx";
import SignUpOtpSection from "../sign-up-otp-section/SignUpOtpSection.tsx";
import SignUpPasswordFields from "../sign-up-password-fields/SignUpPasswordFields.tsx";
import SocialSignUpButtons from "../social-sign-up-buttons/SocialSignUpButtons.tsx";

interface SignUpFormProps
{
    signUp: SignUpState;
}

const SignUpForm = ({signUp}: SignUpFormProps) =>
{
    const navigate = useNavigate();
    const signUpStyles = useSignUpStyles();
    const authorizationStyles = useAuthorizationStyles();
    const globalStyles = useGlobalStyles();

    const onEmailKeyDown = (event: KeyboardEvent<HTMLInputElement>) =>
    {
        if (event.key === 'Enter')
        {
            void signUp.onInitiateSignUp();
        }
    };

    return (
        <div id={"sign-up-div-2"}
             className={authorizationStyles.authorizationFormSection}>
            <Subtitle1 id={"sign-up-subtitle1-1"}
                       align={"center"}>
                <Button
                    id={"sign-up-back-btn"}
                    shape={"circular"}
                    icon={<ArrowLeftRegular/>}
                    appearance={"transparent"}
                    onClick={() => navigate("/sign-in")}/>
                Create account
            </Subtitle1>
            <SignUpErrorMessage message={signUp.responseErrorMessage}
                                onDismiss={signUp.onDismissError}/>
            <Field
                id={"sign-up-field-4"}
                label={"Enter your email"}
                validationState={signUp.initiationSuccessfulMsg ? "success" : "none"}
                validationMessage={signUp.initiationSuccessfulMsg}>
                <Input
                    id={"sign-up-email-input"}
                    type="email"
                    name="email"
                    maxLength={254}
                    autoComplete={"false"}
                    value={signUp.formData.email}
                    onChange={signUp.handleChange}
                    onKeyDown={onEmailKeyDown}
                />
            </Field>
            {signUp.initiationSuccessful && <div id={"sign-up-div-3"}
                                                  className={signUpStyles.signUpCompletionForm}>
                <SignUpOtpSection signUp={signUp}/>
                <SignUpPasswordFields signUp={signUp}/>
                <Button
                    id={"sign-up-complete-btn"}
                    onClick={signUp.onCompleteSignUp}
                    appearance={"primary"}
                    shape={"circular"}
                    disabled={signUp.regeneratingOtp}
                    className={globalStyles.buttonWithLoading}>
                    {signUp.completingSignUp && <Spinner id={"sign-up-spinner-2"}
                                                         size={"tiny"}/>}
                    {signUp.completingSignUp ? "Completing sign up" : "Complete sign up"}
                </Button>
            </div>}
            {!signUp.initiationSuccessful &&
                <Button
                    id={"sign-up-initiate-btn"}
                    onClick={signUp.onInitiateSignUp}
                    appearance={"primary"}
                    shape={"circular"}
                    className={globalStyles.buttonWithLoading}>
                    {signUp.initiatingSignUp && <Spinner id={"sign-up-spinner-3"}
                                                         size={"tiny"}/>}
                    Sign Up
                </Button>}
            {!signUp.initiationSuccessful && <>
                <div id={"sign-up-div-4"}><Divider id={"sign-up-divider-1"}>OR</Divider></div>
                <SocialSignUpButtons/>
            </>}
            <div id={"sign-up-div-5"}
                 className={signUpStyles.authHasAccount}>
                <Caption1 id={"sign-up-caption1-1"}> Already have an account? &nbsp;
                    <Link id={"sign-up-link-1"}
                          onClick={() => navigate("/sign-in")}
                          disabled={signUp.initiatingSignUp || signUp.completingSignUp}>
                        <Text id={"sign-up-text-2"}
                              weight="semibold">Sign in</Text>
                    </Link>
                </Caption1>
            </div>
        </div>
    );
};

export default SignUpForm;
