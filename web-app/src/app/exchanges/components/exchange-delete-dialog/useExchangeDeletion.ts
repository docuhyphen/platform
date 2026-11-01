import React, {useEffect, useState} from "react";
import {ExchangeDetailedDto} from "../../../models/models.tsx";
import {deleteExchange} from "../../../../services/exchangeApi.ts";
import {publishExchangeDelete} from "../../../observable/exchangeObservables.ts";

const COUNTDOWN_SECONDS = 10;
const CANCELLED_COUNTDOWN_SECONDS = 5;

export interface ExchangeDeletion
{
    deletingExchange: boolean;
    deleteStarted: boolean;
    countdown: number;
    dialogErrorMessage: string | null;
    onDelete: () => void;
    onCancel: () => void;
}

export const useExchangeDeletion = (
    isOpen: boolean,
    exchange: ExchangeDetailedDto,
    onExchangeDeleted: (exchangeId: string) => void,
    onDismiss: () => void,
): ExchangeDeletion =>
{
    const [deletingExchange, setDeletingExchange] = useState(false);
    const [deleteStarted, setDeleteStarted] = useState(false);
    const [countdown, setCountdown] = useState(COUNTDOWN_SECONDS);
    const timerRef = React.useRef<ReturnType<typeof setInterval> | null>(null);
    const [dialogErrorMessage, setDialogErrorMessage] = useState<string | null>(null);

    useEffect(() =>
    {
        if (isOpen) setDialogErrorMessage(null);
    }, [isOpen]);

    const completeDeletion = async () =>
    {
        setDeletingExchange(true);

        try
        {
            await deleteExchange(exchange.id);
            onExchangeDeleted(exchange.id);
            publishExchangeDelete(exchange.id);
            onDismiss();
        }
        catch (error)
        {
            setDialogErrorMessage("Error deleting exchange");
            console.error("Error deleting document:", error);
        }
        finally
        {
            setDeletingExchange(false);
            setDeleteStarted(false);
        }
    };

    const onDelete = () =>
    {
        setDeleteStarted(true);
        setCountdown(COUNTDOWN_SECONDS);

        timerRef.current = setInterval(() =>
        {
            setCountdown(prevCountdown =>
            {
                if (prevCountdown <= 1)
                {
                    clearInterval(timerRef.current!);
                    completeDeletion();
                    return 0;
                }
                return prevCountdown - 1;
            });
        }, 1000);
    };

    const onCancel = () =>
    {
        if (timerRef.current)
        {
            clearInterval(timerRef.current);
        }
        setDeleteStarted(false);
        setCountdown(CANCELLED_COUNTDOWN_SECONDS);
    };

    return {deletingExchange, deleteStarted, countdown, dialogErrorMessage, onDelete, onCancel};
};
