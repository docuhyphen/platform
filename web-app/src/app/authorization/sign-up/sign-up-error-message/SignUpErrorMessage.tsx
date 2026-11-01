import {Button, MessageBar, MessageBarActions, MessageBarBody, Text} from "@fluentui/react-components";
import {DismissRegular} from "@fluentui/react-icons";

interface SignUpErrorMessageProps
{
    message?: string;
    onDismiss: () => void;
}

const SignUpErrorMessage = ({message, onDismiss}: SignUpErrorMessageProps) =>
{
    if (!message)
    {
        return null;
    }

    return (
        <MessageBar id={"sign-up-message-bar-1"}
                    intent={"error"}>
            <MessageBarBody id={"sign-up-message-bar-body-1"}>
                <Text id={"sign-up-text-1"}
                      size={200}> {message} </Text>
            </MessageBarBody>
            <MessageBarActions
                id={"sign-up-message-bar-actions-1"}
                containerAction={
                    <Button
                        id={"sign-up-error-dismiss-btn"}
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

export default SignUpErrorMessage;
