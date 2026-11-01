import React from 'react';
import {Button, MessageBar, MessageBarActions, MessageBarBody, MessageBarGroup} from "@fluentui/react-components";
import {DismissRegular} from "@fluentui/react-icons";
import {useExchangeInitiationErrorMessagesStyles} from "./ExchangeInitiationErrorMessagesStyles.tsx";

interface ExchangeInitiationErrorMessagesProps
{
    messages: string[];
    onDismiss: (index: number) => void;
}

const ExchangeInitiationErrorMessages: React.FC<ExchangeInitiationErrorMessagesProps> = ({messages, onDismiss}) =>
{
    const styles = useExchangeInitiationErrorMessagesStyles();

    if (!messages)
    {
        return null;
    }

    return (
        <MessageBarGroup id={"exchange-initiation-error-messages-group"}
                         className={styles.errorMessagesGroup}>
            {messages.map((message: string, index: number) => (
                <MessageBar id={`exchange-initiation-message-bar-${index}`}
                            key={index}
                            intent={"warning"}>
                    <MessageBarBody id={`exchange-initiation-message-bar-body-${index}`}>
                        {message}
                    </MessageBarBody>
                    <MessageBarActions id={`exchange-initiation-message-bar-actions-${index}`}
                                       containerAction={
                                           <Button id={`exchange-close-message-bar-${index}`}
                                                   onClick={() => onDismiss(index)}
                                                   appearance="transparent"
                                                   shape={"circular"}
                                                   icon={<DismissRegular/>}/>
                                       }/>
                </MessageBar>
            ))}
        </MessageBarGroup>
    );
};

export default ExchangeInitiationErrorMessages;
