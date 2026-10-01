import {KeyboardEvent} from "react";
import {Button, Field, Input, Spinner} from "@fluentui/react-components";
import {SignInInputChangeHandler} from "../useSignInFlow.ts";

interface SignInPasswordStepProps
{
    email: string;
    password: string;
    signingIn: boolean;
    buttonWithLoadingClassName: string;
    onPasswordChange: SignInInputChangeHandler;
    onSubmit: () => void;
}

const SignInPasswordStep = ({
    email,
    password,
    signingIn,
    buttonWithLoadingClassName,
    onPasswordChange,
    onSubmit,
}: SignInPasswordStepProps) =>
{
    const onPasswordKeyDown = (event: KeyboardEvent<HTMLInputElement>) =>
    {
        if (event.key === 'Enter')
        {
            onSubmit();
        }
    };

    return <>
        <Field
            id={"sign-in-email-readonly-field"}
            label={"Email"}>
            <Input
                id={"sign-in-email-readonly-input"}
                value={email}
                type="email"
                disabled
            />
        </Field>
        <Field
            id={"sign-in-password-field"}
            label={"Password"}
            validationState={"none"}
            validationMessage={""}>
            <Input
                id={"sign-in-password-input"}
                type="password"
                value={password}
                maxLength={30}
                onChange={onPasswordChange}
                onKeyDown={onPasswordKeyDown}
            />
        </Field>
        <Button
            id={"sign-in-submit-btn"}
            onClick={onSubmit}
            appearance="primary"
            className={buttonWithLoadingClassName}
            shape={"circular"}>
            {signingIn && <><Spinner
                id={"sign-in-submit-spinner"}
                size={"tiny"}
            /> Signing in</>}
            {!signingIn && "Sign In"}
        </Button>
    </>;
};

export default SignInPasswordStep;
