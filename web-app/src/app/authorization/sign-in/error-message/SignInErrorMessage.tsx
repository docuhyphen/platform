import {Button, MessageBar, MessageBarActions, MessageBarBody} from "@fluentui/react-components";
import {DismissRegular} from "@fluentui/react-icons";

interface SignInErrorMessageProps
{
    message?: string;
    onDismiss: () => void;
}

const SignInErrorMessage = ({message, onDismiss}: SignInErrorMessageProps) =>
{
    if (!message)
    {
        return null;
    }

    return <MessageBar
        id={"sign-in-error-message"}
        intent={"error"}>
        <MessageBarBody id={"sign-in-error-message-body"}>
            {message}
        </MessageBarBody>
        <MessageBarActions
            id={"sign-in-error-message-actions"}
            containerAction={
                <Button
                    id={"sign-in-error-dismiss-btn"}
                    shape={"circular"}
                    onClick={onDismiss}
                    appearance="transparent"
                    icon={<DismissRegular/>}
                />
            }
        />
    </MessageBar>;
};

export default SignInErrorMessage;
