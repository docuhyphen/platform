import {MessageBar, MessageBarBody, Text} from "@fluentui/react-components";
import {useSetupPasswordDialogStyles} from "./SetupPasswordDialogStyles.tsx";

const SetupPasswordSuccess = () =>
{
    const styles = useSetupPasswordDialogStyles();

    return (
        <div
            id={"setup-password-success-content"}
            className={styles.successContent}>
            <MessageBar
                id={"setup-password-success-message"}
                intent={"success"}>
                <MessageBarBody>Email and password sign-in has been added successfully.</MessageBarBody>
            </MessageBar>
            <Text id={"setup-password-sign-in-guidance"}>
                For your security, your existing sessions have been signed out. Continue to sign in
                again using a connected provider or your new email and password.
            </Text>
        </div>
    );
};

export default SetupPasswordSuccess;
