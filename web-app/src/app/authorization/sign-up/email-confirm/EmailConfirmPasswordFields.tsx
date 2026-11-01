import {ChangeEvent, KeyboardEvent} from "react";
import {Field, InfoLabel, Input, InputOnChangeData} from "@fluentui/react-components";

interface EmailConfirmPasswordFieldsProps
{
    password: string;
    confirmationPassword: string;
    disabled: boolean;
    onChange: (event: ChangeEvent<HTMLInputElement>, data: InputOnChangeData) => void;
    onKeyDown: (event: KeyboardEvent<HTMLInputElement>) => void;
}

const EmailConfirmPasswordFields = ({
                                        password,
                                        confirmationPassword,
                                        disabled,
                                        onChange,
                                        onKeyDown
                                    }: EmailConfirmPasswordFieldsProps) =>
{
    return <>
        <Field
            id={"email-confirm-password-field"}
            label={"Password"}
            validationState={"none"}
            validationMessage={""}>
            <Input
                id={"email-confirm-password-input"}
                type="password"
                name="password"
                maxLength={30}
                value={password}
                disabled={disabled}
                onChange={onChange}
                onKeyDown={onKeyDown}
                contentAfter={
                    <InfoLabel
                        id={"email-confirm-password-requirements"}
                        info={<>
                            <strong id={"email-confirm-form-strong-2"}>Password requirements</strong>
                            <ul id={"email-confirm-form-ul-1"}>
                                <li id={"email-confirm-form-li-1"}>Must be at least 12 characters long</li>
                                <li id={"email-confirm-form-li-2"}>Must not exceed 128 characters</li>
                                <li id={"email-confirm-form-li-3"}>Must contain at least one uppercase letter</li>
                                <li id={"email-confirm-form-li-4"}>Must contain at least one lowercase letter</li>
                                <li id={"email-confirm-form-li-5"}>Must contain at least one digit</li>
                                <li id={"email-confirm-form-li-6"}>Must contain at least one special character</li>
                            </ul>
                        </>}
                    />
                }
            />
        </Field>
        <Field
            id={"email-confirm-confirm-password-field"}
            label={"Password Confirmation"}
            validationState={"none"}
            validationMessage={""}>
            <Input
                id={"email-confirm-confirm-password-input"}
                type={"password"}
                name="confirmationPassword"
                maxLength={30}
                value={confirmationPassword}
                disabled={disabled}
                onChange={onChange}
                onKeyDown={onKeyDown}
            />
        </Field>
    </>;
};

export default EmailConfirmPasswordFields;
