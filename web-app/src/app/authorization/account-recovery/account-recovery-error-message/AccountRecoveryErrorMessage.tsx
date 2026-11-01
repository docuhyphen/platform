import {Button, MessageBar, MessageBarActions, MessageBarBody, Text} from "@fluentui/react-components";
import {DismissRegular} from "@fluentui/react-icons";

interface AccountRecoveryErrorMessageProps
{
    message?: string;
    onDismiss: () => void;
}

const AccountRecoveryErrorMessage = ({message, onDismiss}: AccountRecoveryErrorMessageProps) =>
{
    if (!message)
    {
        return null;
    }

    return (
        <MessageBar id={"account-recovery-message-bar-1"}
                    intent={"error"}>
            <MessageBarBody id={"account-recovery-message-bar-body-1"}>
                <Text id={"account-recovery-text-1"}
                      size={200}> {message} </Text>
            </MessageBarBody>
            <MessageBarActions
                id={"account-recovery-message-bar-actions-1"}
                containerAction={
                    <Button
                        id={"account-recovery-error-dismiss-btn"}
                        shape={"circular"}
                        onClick={onDismiss}
                        appearance="transparent"
                        icon={<DismissRegular/>}
                    />
                }
            />
        </MessageBar>
    );
};

export default AccountRecoveryErrorMessage;
