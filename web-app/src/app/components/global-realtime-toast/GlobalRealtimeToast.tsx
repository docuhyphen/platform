import React, {useEffect} from 'react';
import {
    Toast,
    ToastBody,
    Toaster,
    ToastTitle,
    useId,
    useToastController,
} from '@fluentui/react-components';

interface GlobalRealtimeToastProps
{
    event: { id: number; message: string } | null;
}

const GlobalRealtimeToast: React.FC<GlobalRealtimeToastProps> = ({event}) =>
{
    const toasterId = useId('global-realtime-toaster');
    const {dispatchToast} = useToastController(toasterId);

    useEffect(() =>
    {
        if (!event) return;

        dispatchToast(
            <Toast id={`global-realtime-toast-${event.id}`}>
                <ToastTitle id={`global-realtime-toast-title-${event.id}`}>
                    Realtime message
                </ToastTitle>
                <ToastBody id={`global-realtime-toast-body-${event.id}`}>
                    {event.message}
                </ToastBody>
            </Toast>,
            {
                intent: 'info',
                timeout: 4500,
            },
        );
    }, [dispatchToast, event]);

    return (
        <Toaster
            id="global-realtime-toaster"
            toasterId={toasterId}
            position="bottom-end"
        />
    );
};

export default GlobalRealtimeToast;
