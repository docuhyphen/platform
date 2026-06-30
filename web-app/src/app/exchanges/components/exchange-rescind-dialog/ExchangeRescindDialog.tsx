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
            <DialogSurface>
                <DialogBody>
                    <DialogTitle>Rescind Exchange: {exchange?.name}</DialogTitle>
                    <DialogContent>
                        {dialogErrorMessage && (
                            <MessageBar intent="error">
                                <MessageBarBody>
                                    <Text size={200}>{dialogErrorMessage}</Text>
                                </MessageBarBody>
                            </MessageBar>
                        )}
                        <Text>
                            This will cancel the exchange without deleting it and move it to archive.
                        </Text>
                    </DialogContent>
                    <DialogActions>
                        <Button
                            id={"rescind-exchange-submit-btn"}
                            appearance="primary"
                            className={globalStyles.buttonWithLoading}
                            shape={"circular"}
                            onClick={onConfirmRescind}>
                            {isSubmitting && <Spinner size={"tiny"}/>}
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
