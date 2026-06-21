import {ExchangeDetailedDto, ExchangeStatus, UpdateExchangeRequest} from "../../../models/models.tsx";
import React, {useEffect, useState} from "react";
import useToken from "../../../../context/useToken.tsx";
import {useGlobalStyles} from "../../../../GlobalStyles.tsx";
import {
    Button,
    Dialog,
    DialogActions,
    DialogBody,
    DialogContent,
    DialogSurface,
    DialogTitle,
    DialogTrigger,
    Field,
    MessageBar,
    MessageBarBody,
    Spinner,
    Text,
    Textarea
} from "@fluentui/react-components";
import {useExchangeEndDialogStyles} from "./ExchangeEndDialogStyles.tsx";
import {fetchSignedInUserAppUserExchange, updateExchange} from "../../../../services/exchangeApi.ts";
import {publishExchangeUpdate} from "../../../observable/exchangeObservables.ts";

interface ExchangeEndDialogProps
{
    isOpen: boolean;
    onDismiss: () => void;
    exchange: ExchangeDetailedDto;
    onExchangeEnded: (exchange: ExchangeDetailedDto) => void;
}

const ExchangeEndDialog: React.FC<ExchangeEndDialogProps> = (
    {
        isOpen,
        onDismiss,
        exchange,
        onExchangeEnded
    }) =>
{
    const styles = useExchangeEndDialogStyles()
    const token = useToken();
    const [exchangeEndNote, setExchangeEndNote] = React.useState('');
    const [endingExchange, setEndingExchange] = React.useState(false);
    const globalStyles = useGlobalStyles()
    const [dialogErrorMessage, setDialogErrorMessage] = useState<string | null>(null);

    useEffect(() =>
    {
        if (isOpen) setDialogErrorMessage(null);
    }, [isOpen]);

    const onExchangeEnd = async () =>
    {
        setEndingExchange(true)

        try
        {
            const request = {
                status: ExchangeStatus.ENDED
            } as UpdateExchangeRequest

            await updateExchange(exchange.id, request, token);
            const updatedExchange = await fetchSignedInUserAppUserExchange(exchange.id, token);
            publishExchangeUpdate(updatedExchange as ExchangeDetailedDto);
            onExchangeEnded(updatedExchange as ExchangeDetailedDto);
            setExchangeEndNote('');
            onDismiss()
        }
        catch (error)
        {
            setDialogErrorMessage("Error ending exchange");
            console.error("Error ending exchange", error);
        }
        finally
        {
            setEndingExchange(false);
        }
    }

    const onEndNoteChange = (_, newValue) =>
    {
        setExchangeEndNote(newValue.value || '')
    }

    const onCancel = () =>
    {
        setExchangeEndNote('');
        onDismiss();
    }

    return <>
        {<Dialog modalType="alert"
                 open={isOpen}>
            <DialogSurface>
                <DialogBody>
                    <DialogTitle>Ending Exchange: {exchange && exchange.name}</DialogTitle>
                    <DialogContent className={styles.dialogContentContainer}>
                        {dialogErrorMessage && (
                            <MessageBar intent="error">
                                <MessageBarBody>
                                    <Text size={200}>{dialogErrorMessage}</Text>
                                </MessageBarBody>
                            </MessageBar>
                        )}
                        <Field label={"Notes"} className={styles.endNoteField}>
                            <Textarea value={exchangeEndNote}
                                      onChange={onEndNoteChange}/>
                        </Field>
                    </DialogContent>
                    <DialogActions>
                        <Button appearance="primary"
                                className={globalStyles.buttonWithLoading}
                                shape={"circular"}
                                onClick={onExchangeEnd}>
                            {endingExchange && <Spinner size={"tiny"}/>}
                            End Exchange
                        </Button>
                        <DialogTrigger disableButtonEnhancement>
                            <Button appearance="secondary"
                                    shape={"circular"}
                                    disabled={endingExchange}
                                    onClick={onCancel}>
                                Cancel
                            </Button>
                        </DialogTrigger>
                    </DialogActions>
                </DialogBody>
            </DialogSurface>
        </Dialog>
        }
    </>
}

export default ExchangeEndDialog;