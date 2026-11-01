import React from 'react';
import {Link, Toast, ToastTitle, ToastTrigger} from "@fluentui/react-components";

interface ExchangeInitiationErrorToastProps
{
    message: string;
}

const ExchangeInitiationErrorToast: React.FC<ExchangeInitiationErrorToastProps> = ({message}) =>
{
    return (
        <Toast id={"exchange-initiation-toast"}>
            <ToastTitle id={"exchange-initiation-message-toast-title"}
                        action={
                            <ToastTrigger>
                                <Link id={"exchange-initiation-dismiss-link"}>Dismiss</Link>
                            </ToastTrigger>
                        }>
                {message}
            </ToastTitle>
        </Toast>
    );
};

export default ExchangeInitiationErrorToast;
