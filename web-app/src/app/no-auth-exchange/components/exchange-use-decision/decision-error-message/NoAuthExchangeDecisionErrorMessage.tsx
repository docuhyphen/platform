import React from "react";
import {Button, MessageBar, MessageBarActions, MessageBarBody} from "@fluentui/react-components";
import {DismissRegular} from "@fluentui/react-icons";

interface NoAuthExchangeDecisionErrorMessageProps
{
    errorMessage: string | undefined;
    onDismiss: () => void;
}

const NoAuthExchangeDecisionErrorMessage: React.FC<NoAuthExchangeDecisionErrorMessageProps> = (
    {
        errorMessage,
        onDismiss
    }) =>
{
    if (!errorMessage)
    {
        return null;
    }

    return (
        <MessageBar id={"no-auth-exchange-user-decision-message-bar-1"}
                    intent={"error"}>
            <MessageBarBody id={"no-auth-exchange-user-decision-message-bar-body-1"}>
                {errorMessage}
            </MessageBarBody>
            <MessageBarActions
                id={"no-auth-exchange-user-decision-message-bar-actions-1"}
                containerAction={
                    <Button
                        id={"no-auth-exchange-decision-dismiss-error-btn"}
                        onClick={onDismiss}
                        appearance="transparent"
                        shape={"circular"}
                        icon={<DismissRegular/>}
                    />
                }
            />
        </MessageBar>
    );
};

export default NoAuthExchangeDecisionErrorMessage;
