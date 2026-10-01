import {KeyboardEvent} from "react";
import {Button, Divider, Field, Input, Spinner} from "@fluentui/react-components";
import SocialSignInButtons from "../social-sign-in-buttons/SocialSignInButtons.tsx";
import {SignInInputChangeHandler} from "../useSignInFlow.ts";

interface SignInEmailStepProps
{
    email: string;
    lookingUp: boolean;
    buttonWithLoadingClassName: string;
    onEmailChange: SignInInputChangeHandler;
    onContinue: () => void;
}

const SignInEmailStep = ({
    email,
    lookingUp,
    buttonWithLoadingClassName,
    onEmailChange,
    onContinue,
}: SignInEmailStepProps) =>
{
    const onEmailKeyDown = (event: KeyboardEvent<HTMLInputElement>) =>
    {
        if (event.key === 'Enter')
        {
            onContinue();
        }
    };

    return <>
        <Field
            id={"sign-in-email-field"}
            label={"Email"}
            validationState={"none"}
            validationMessage={""}>
            <Input
                id={"sign-in-email-input"}
                value={email}
                type="email"
                maxLength={254}
                onChange={onEmailChange}
                onKeyDown={onEmailKeyDown}
            />
        </Field>
        <Button
            id={"sign-in-continue-btn"}
            onClick={onContinue}
            appearance="primary"
            className={buttonWithLoadingClassName}
            shape={"circular"}>
            {lookingUp && <><Spinner
                id={"sign-in-continue-spinner"}
                size={"tiny"}
            /> Continue...</>}
            {!lookingUp && "Continue"}
        </Button>
        <div id={"sign-in-provider-divider"}>
            <Divider id={"sign-in-provider-divider-line"}>OR</Divider>
        </div>
        <SocialSignInButtons/>
    </>;
};

export default SignInEmailStep;
