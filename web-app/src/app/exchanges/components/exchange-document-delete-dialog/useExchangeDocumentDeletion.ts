import React, {useEffect, useState} from "react";
import {DocumentDetailedDto, ExchangeDetailedDto} from "../../../models/models.tsx";
import {deleteExchangeDocument} from "../../../../services/exchangeApi.ts";

const COUNTDOWN_SECONDS = 10;

export interface ExchangeDocumentDeletion
{
    deletingDocument: boolean;
    deleteStarted: boolean;
    countdown: number;
    dialogErrorMessage: string | null;
    onDelete: () => void;
    onCancel: () => void;
    onDismiss: () => void;
}

export const useExchangeDocumentDeletion = (
    isOpen: boolean,
    exchange: ExchangeDetailedDto,
    exchangeDocument: DocumentDetailedDto,
    onDocumentDeleted: (documentId: string) => void,
    onClose: () => void,
): ExchangeDocumentDeletion =>
{
    const [deletingDocument, setDeletingDocument] = useState(false);
    const [deleteStarted, setDeleteStarted] = useState(false);
    const [countdown, setCountdown] = useState(COUNTDOWN_SECONDS);
    const timerRef = React.useRef<ReturnType<typeof setInterval> | null>(null);
    const [dialogErrorMessage, setDialogErrorMessage] = useState<string | null>(null);

    useEffect(() =>
    {
        if (isOpen) setDialogErrorMessage(null);
    }, [isOpen]);

    const clearTimer = () =>
    {
        if (timerRef.current)
        {
            clearInterval(timerRef.current);
        }
    };

    const completeDeletion = async () =>
    {
        setDeletingDocument(true);

        try
        {
            await deleteExchangeDocument(exchange.id, exchangeDocument.id);
            onDocumentDeleted(exchangeDocument.id);
        }
        catch (error)
        {
            setDialogErrorMessage("Error deleting document");
            console.error("Error deleting document:", error);
        }
        finally
        {
            setDeletingDocument(false);
            setDeleteStarted(false);
            onClose();
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
        clearTimer();
        setDeleteStarted(false);
        setCountdown(COUNTDOWN_SECONDS);
    };

    const onDismiss = () =>
    {
        onCancel();
        onClose();
    };

    return {deletingDocument, deleteStarted, countdown, dialogErrorMessage, onDelete, onCancel, onDismiss};
};
