import {createElement, useRef} from 'react';
import {useToastController} from '@fluentui/react-components';
import ExchangeUnavailableToast from './ExchangeUnavailableToast.tsx';

export type DispatchToast = ReturnType<typeof useToastController>['dispatchToast'];

export const useExchangeUnavailableNotice = (dispatchToast: DispatchToast, onOpenActiveTab: () => void) =>
{
    const lastUnavailableExchangeIdRef = useRef<string | null>(null);

    return (exchangeId?: string | null) =>
    {
        const normalizedExchangeId = exchangeId ?? '__unknown__';
        if (lastUnavailableExchangeIdRef.current === normalizedExchangeId)
        {
            return;
        }

        lastUnavailableExchangeIdRef.current = normalizedExchangeId;
        dispatchToast(
            createElement(ExchangeUnavailableToast, {onOpenActiveTab}),
            {intent: 'warning', timeout: 7000}
        );
    };
};
