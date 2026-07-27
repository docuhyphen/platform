import {useState} from 'react';
import {ExchangeDetailedDto, ExchangeStatus, UpdateExchangeRequest} from '../../../models/models.tsx';
import {fetchSignedInUserAppUserExchange, updateExchange} from '../../../../services/exchangeApi.ts';
import {publishExchangeUpdate} from '../../../observable/exchangeObservables.ts';
import {normalizeApiError} from '../../../../utils/apiErrorUtils.ts';

export const useExchangeAcceptanceDecision = (
    exchange: ExchangeDetailedDto | null,
    onAccepted: (exchange: ExchangeDetailedDto) => void,
    onRejected: (exchangeId: string) => void,
) =>
{
    const [updatingExchange, setUpdatingExchange] = useState(false);
    const [rejectingExchange, setRejectingExchange] = useState(false);
    const [rejectReason, setRejectReason] = useState('');
    const [dialogErrorMessage, setDialogErrorMessage] = useState<string | null>(null);

    const decide = async (status: ExchangeStatus) =>
    {
        if (!exchange || updatingExchange)
        {
            return;
        }
        setUpdatingExchange(true);
        setDialogErrorMessage(null);
        try
        {
            const request: UpdateExchangeRequest = {status};
            if (status === ExchangeStatus.REJECTED)
            {
                request.rejectionReason = rejectReason;
            }
            await updateExchange(exchange.id, request);
            if (status === ExchangeStatus.REJECTED)
            {
                setRejectReason('');
                onRejected(exchange.id);
                return;
            }
            const updatedExchange = await fetchSignedInUserAppUserExchange(exchange.id) as ExchangeDetailedDto;
            publishExchangeUpdate(updatedExchange);
            onAccepted(updatedExchange);
        }
        catch (error)
        {
            setDialogErrorMessage(normalizeApiError(error, 'Error updating Exchange').message);
        }
        finally
        {
            setUpdatingExchange(false);
        }
    };

    const cancelDecline = () =>
    {
        setRejectingExchange(false);
        setRejectReason('');
    };

    return {
        updatingExchange,
        rejectingExchange,
        rejectReason,
        dialogErrorMessage,
        setRejectReason,
        beginDecline: () => setRejectingExchange(true),
        cancelDecline,
        accept: () => decide(ExchangeStatus.ACCEPTED_STARTED),
        reject: () => decide(ExchangeStatus.REJECTED),
    };
};
