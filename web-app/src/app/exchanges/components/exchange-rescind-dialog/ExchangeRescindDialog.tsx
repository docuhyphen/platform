import React, {useEffect, useState} from "react";
import {
    Button,
    Dialog,
    DialogActions,
    DialogBody,
    DialogContent,
    DialogSurface,
    DialogTitle,
    DialogTrigger,
    MessageBar,
    MessageBarBody,
    Spinner,
    Text,
} from "@fluentui/react-components";
import {useGlobalStyles} from "../../../../GlobalStyles.tsx";
import {ExchangeDetailedDto} from "../../../models/models.tsx";
import {
    fetchSignedInUserAppUserExchange,
    rescindExchange,
} from "../../../../services/exchangeApi.ts";
import {publishExchangeUpdate} from "../../../observable/exchangeObservables.ts";

interface ExchangeRescindDialogProps
{
    isOpen: boolean;
    onDismiss: () => void;
    exchange: ExchangeDetailedDto;
    onExchangeRescinded: (exchange: ExchangeDetailedDto) => void;
}

const ExchangeRescindDialog: React.FC<ExchangeRescindDialogProps> = (
    {
        isOpen,
        onDismiss,
        exchange,
        onExchangeRescinded,
    }) =>
{
    const globalStyles = useGlobalStyles();
    const [isSubmitting, setIsSubmitting] = useState(false);
    const [dialogErrorMessage, setDialogErrorMessage] = useState<string | null>(null);

    useEffect(() =>
    {
        if (isOpen)
        {
            setDialogErrorMessage(null);
        }
    }, [isOpen]);

    const onConfirmRescind = async () =>
    {
        setIsSubmitting(true);

        try
        {
            await rescindExchange(exchange.id);
            const updatedExchange = await fetchSignedInUserAppUserExchange(exchange.id);
            publishExchangeUpdate(updatedExchange as ExchangeDetailedDto);
            onExchangeRescinded(updatedExchange as ExchangeDetailedDto);
            onDismiss();
        }
        catch (error)
        {
            setDialogErrorMessage("Error rescinding exchange");
            console.error("Error rescinding exchange", error);
        }
        finally
        {
            setIsSubmitting(false);
        }
    };

    return (
        <Dialog
            modalType="alert"
            open={isOpen}>
            <DialogSurface id={"exchange-rescind-dialog-dialog-surface-1"}>
                <DialogBody id={"exchange-rescind-dialog-dialog-body-1"}>
                    <DialogTitle id={"exchange-rescind-dialog-dialog-title-1"}>Rescind Exchange: {exchange?.name}</DialogTitle>
                    <DialogContent id={"exchange-rescind-dialog-dialog-content-1"}>
                        {dialogErrorMessage && (
                            <MessageBar id={"exchange-rescind-dialog-message-bar-1"}
                                        intent="error">
                                <MessageBarBody id={"exchange-rescind-dialog-message-bar-body-1"}>
                                    <Text id={"exchange-rescind-dialog-text-1"}
                                          size={200}>{dialogErrorMessage}</Text>
                                </MessageBarBody>
                            </MessageBar>
                        )}
                        <Text id={"exchange-rescind-dialog-text-2"}>
                            This will cancel the Exchange without deleting it and move it to archive.
                        </Text>
                    </DialogContent>
                    <DialogActions id={"exchange-rescind-dialog-dialog-actions-1"}>
                        <Button
                            id={"rescind-exchange-submit-btn"}
                            appearance="primary"
                            className={globalStyles.buttonWithLoading}
                            shape={"circular"}
                            onClick={onConfirmRescind}>
                            {isSubmitting && <Spinner id={"exchange-rescind-dialog-spinner-1"}
                                                      size={"tiny"}/>}
                            Rescind Exchange
                        </Button>
                        <DialogTrigger disableButtonEnhancement>
                            <Button
                                id={"rescind-exchange-cancel-btn"}
                                appearance="secondary"
                                shape={"circular"}
                                disabled={isSubmitting}
                                onClick={onDismiss}>
                                Cancel
                            </Button>
                        </DialogTrigger>
                    </DialogActions>
                </DialogBody>
            </DialogSurface>
        </Dialog>
    );
};

export default ExchangeRescindDialog;
