import {KeyboardEvent} from "react";
import {Field, InfoLabel, Input} from "@fluentui/react-components";
import {AccountRecoveryState} from "../useAccountRecovery.ts";

interface AccountRecoveryPasswordFieldsProps
{
    recovery: AccountRecoveryState;
}

const AccountRecoveryPasswordFields = ({recovery}: AccountRecoveryPasswordFieldsProps) =>
{
    const onKeyDown = (event: KeyboardEvent<HTMLInputElement>) =>
    {
        if (event.key === 'Enter')
        {
            void recovery.onCompletePasswordReset();
        }
    };

    return (
        <>
            <Field
                id={"account-recovery-field-2"}
                label={"Password"}
                validationState={"none"}
                validationMessage={""}>
                <Input
                    id={"account-recovery-password-input"}
                    type="password"
                    name="password"
                    maxLength={30}
                    value={recovery.formData.password}
                    disabled={recovery.regeneratingOtp}
                    onChange={recovery.handleChange}
                    onKeyDown={onKeyDown}
                    contentAfter={
                        <InfoLabel
                            id={"account-recovery-info-label-1"}
                            info={<>
                                <strong id={"account-recovery-strong-1"}>Password requirements</strong>
                                <ul id={"account-recovery-ul-1"}>
                                    <li id={"account-recovery-li-1"}>Must be at least 12 characters long</li>
                                    <li id={"account-recovery-li-2"}>Must not exceed 128 characters</li>
                                    <li id={"account-recovery-li-3"}>Must contain at least one uppercase letter</li>
                                    <li id={"account-recovery-li-4"}>Must contain at least one lowercase letter</li>
                                    <li id={"account-recovery-li-5"}>Must contain at least one digit</li>
                                    <li id={"account-recovery-li-6"}>Must contain at least one special character</li>
                                </ul>
                            </>}
                        />
                    }
                />
            </Field>
            <Field
                id={"account-recovery-field-3"}
                label={"Password Confirmation"}
                validationState={"none"}
                validationMessage={""}>
                <Input
                    id={"account-recovery-confirm-password-input"}
                    type={"password"}
                    name="confirmationPassword"
                    maxLength={30}
                    value={recovery.formData.confirmationPassword}
                    disabled={recovery.regeneratingOtp}
                    onChange={recovery.handleChange}
                    onKeyDown={onKeyDown}
                />
            </Field>
        </>
    );
};

export default AccountRecoveryPasswordFields;
