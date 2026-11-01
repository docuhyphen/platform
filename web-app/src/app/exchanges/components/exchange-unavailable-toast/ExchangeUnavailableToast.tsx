import React from 'react';
import {Link, Toast, ToastBody, ToastTitle, ToastTrigger} from '@fluentui/react-components';

interface ExchangeUnavailableToastProps
{
    onOpenActiveTab: () => void;
}

const ExchangeUnavailableToast: React.FC<ExchangeUnavailableToastProps> = ({onOpenActiveTab}) =>
{
    return (
        <Toast id={"exchanges-toast"}>
            <ToastTitle id={"exchanges-this-exchange-is-no-toast-title"}
                        action={
                            <ToastTrigger>
                                <Link id={"exchanges-dismiss-link"}>Dismiss</Link>
                            </ToastTrigger>
                        }>
                This Exchange is no longer available.
            </ToastTitle>
            <ToastBody id={"exchanges-go-to-active-tab-toast-body"}>
                <Link id={"exchanges-go-to-active-tab-link"}
                      onClick={onOpenActiveTab}>Go to Active tab</Link>
            </ToastBody>
        </Toast>
    );
};

export default ExchangeUnavailableToast;
