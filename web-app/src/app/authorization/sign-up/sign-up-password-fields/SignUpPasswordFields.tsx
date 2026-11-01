import {KeyboardEvent} from "react";
import {Field, InfoLabel, Input} from "@fluentui/react-components";
import {SignUpState} from "../useSignUp.ts";

interface SignUpPasswordFieldsProps
{
    signUp: SignUpState;
}

const SignUpPasswordFields = ({signUp}: SignUpPasswordFieldsProps) =>
{
    const onKeyDown = (event: KeyboardEvent<HTMLInputElement>) =>
    {
        if (event.key === 'Enter')
        {
            void signUp.onCompleteSignUp();
        }
    };

    return (
        <>
            <Field
                id={"sign-up-field-2"}
                label={"Password"}
                validationState={"none"}
                validationMessage={""}>
                <Input
                    id={"sign-up-password-input"}
                    type="password"
                    name="password"
                    maxLength={30}
                    value={signUp.formData.password}
                    disabled={signUp.regeneratingOtp}
                    onChange={signUp.handleChange}
                    onKeyDown={onKeyDown}
                    contentAfter={
                        <InfoLabel
                            id={"sign-up-info-label-1"}
                            info={<>
                                <strong id={"sign-up-strong-1"}>Password requirements</strong>
                                <ul id={"sign-up-ul-1"}>
                                    <li id={"sign-up-li-1"}>Must be at least 12 characters long</li>
                                    <li id={"sign-up-li-2"}>Must not exceed 128 characters</li>
                                    <li id={"sign-up-li-3"}>Must contain at least one uppercase letter</li>
                                    <li id={"sign-up-li-4"}>Must contain at least one lowercase letter</li>
                                    <li id={"sign-up-li-5"}>Must contain at least one digit</li>
                                    <li id={"sign-up-li-6"}>Must contain at least one special character</li>
                                </ul>
                            </>}
                        />
                    }
                />
            </Field>
            <Field
                id={"sign-up-field-3"}
                label={"Password Confirmation"}
                validationState={"none"}
                validationMessage={""}>
                <Input
                    id={"sign-up-confirm-password-input"}
                    type={"password"}
                    name="confirmationPassword"
                    maxLength={30}
                    value={signUp.formData.confirmationPassword}
                    disabled={signUp.regeneratingOtp}
                    onChange={signUp.handleChange}
                    onKeyDown={onKeyDown}
                />
            </Field>
        </>
    );
};

export default SignUpPasswordFields;
