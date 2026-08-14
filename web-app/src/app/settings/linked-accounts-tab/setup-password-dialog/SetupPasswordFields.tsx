import {Field, Input, MessageBar, MessageBarBody, Text} from "@fluentui/react-components";
import PasswordRequirementsInfo from "./PasswordRequirementsInfo.tsx";
import {useSetupPasswordDialogStyles} from "./SetupPasswordDialogStyles.tsx";

interface SetupPasswordFieldsProps
{
    password: string;
    confirmationPassword: string;
    saving: boolean;
    error?: string;
    onPasswordChange: (value: string) => void;
    onConfirmationPasswordChange: (value: string) => void;
}

const SetupPasswordFields = ({
    password,
    confirmationPassword,
    saving,
    error,
    onPasswordChange,
    onConfirmationPasswordChange,
}: SetupPasswordFieldsProps) =>
{
    const styles = useSetupPasswordDialogStyles();

    return <>
        <Text
            id={"setup-password-dialog-guidance"}
            className={styles.guidance}>
            Create a password so you can sign in without relying on a connected provider.
            You will be signed out on all devices.
        </Text>
        {error && (
            <MessageBar
                id={"setup-password-dialog-error"}
                intent={"error"}>
                <MessageBarBody>{error}</MessageBarBody>
            </MessageBar>
        )}
        <Field label={"Password"}>
            <Input
                id={"setup-password-input"}
                type={"password"}
                value={password}
                maxLength={128}
                disabled={saving}
                onChange={(_, data) => onPasswordChange(data.value)}
                contentAfter={<PasswordRequirementsInfo/>}
            />
        </Field>
        <Field label={"Confirm password"}>
            <Input
                id={"setup-password-confirmation-input"}
                type={"password"}
                value={confirmationPassword}
                maxLength={128}
                disabled={saving}
                onChange={(_, data) => onConfirmationPasswordChange(data.value)}
            />
        </Field>
    </>;
};

export default SetupPasswordFields;
