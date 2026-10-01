import {Button, MessageBar, MessageBarActions, MessageBarBody, Text} from "@fluentui/react-components";
import {DismissRegular} from "@fluentui/react-icons";

interface EmailConfirmErrorBarProps
{
    message?: string;
    onDismiss: () => void;
}

const EmailConfirmErrorBar = ({message, onDismiss}: EmailConfirmErrorBarProps) =>
{
    if (!message)
    {
        return null;
    }

    return <MessageBar
        id={"email-confirm-error-message"}
        intent={"error"}>
        <MessageBarBody id={"email-confirm-error-message-body"}>
            <Text size={200}>{message}</Text>
        </MessageBarBody>
        <MessageBarActions
            id={"email-confirm-error-message-actions"}
            containerAction={
                <Button
                    id={"email-confirm-error-dismiss-btn"}
                    shape={"circular"}
                    onClick={onDismiss}
                    appearance="transparent"
                    icon={<DismissRegular/>}
                />
            }
        />
    </MessageBar>;
};

export default EmailConfirmErrorBar;
