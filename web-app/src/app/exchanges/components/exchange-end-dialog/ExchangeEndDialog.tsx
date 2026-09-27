import {ExchangeDetailedDto} from "../../../models/models.tsx";
import React, {ChangeEvent} from "react";
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
    Textarea,
    TextareaOnChangeData,
} from "@fluentui/react-components";
import {useExchangeEndDialogStyles} from "./ExchangeEndDialogStyles.tsx";
import {useExchangeEnding} from "./useExchangeEnding.ts";

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
    const [exchangeEndNote, setExchangeEndNote] = React.useState('');
    const globalStyles = useGlobalStyles()
    const ending = useExchangeEnding(exchange, isOpen, (updatedExchange) =>
    {
        onExchangeEnded(updatedExchange);
        setExchangeEndNote('');
        onDismiss();
    });

    const onEndNoteChange = (_: ChangeEvent<HTMLTextAreaElement>, data: TextareaOnChangeData) =>
    {
        setExchangeEndNote(data.value || '')
    }

    const onCancel = () =>
    {
        setExchangeEndNote('');
        onDismiss();
    }

    return <>
        {<Dialog modalType="alert"
                 open={isOpen}>
            <DialogSurface id={"end-exchange-dialog"}>
                <DialogBody>
                    <DialogTitle id={"end-exchange-dialog-title"}>Ending Exchange: {exchange && exchange.name}</DialogTitle>
                    <DialogContent id={"end-exchange-dialog-content"}
                                   className={styles.dialogContentContainer}>
                        {ending.message && (
                            <MessageBar id={"end-exchange-message"}
                                        intent={ending.refused ? "warning" : "error"}>
                                <MessageBarBody>
                                    <Text id={"end-exchange-message-text"}
                                          size={200}>
                                        {ending.message}
                                    </Text>
                                </MessageBarBody>
                            </MessageBar>
                        )}
                        <Field id={"end-exchange-note-field"}
                               label={"Notes"}
                               className={styles.endNoteField}>
                            <Textarea
                                id={"textarea-end-exchange-note"}
                                value={exchangeEndNote}
                                onChange={onEndNoteChange}/>
                        </Field>
                    </DialogContent>
                    <DialogActions id={"end-exchange-dialog-actions"}>
                        <Button
                            id={"end-exchange-submit-btn"}
                            appearance="primary"
                            className={globalStyles.buttonWithLoading}
                            shape={"circular"}
                            disabled={ending.ending || ending.blockedByRequests}
                            onClick={ending.end}>
                            {ending.ending && <Spinner size={"tiny"}/>}
                            {ending.cancellationOffered ? "Cancel requests and end" : "End Exchange"}
                        </Button>
                        <DialogTrigger disableButtonEnhancement>
                            <Button
                                id={"end-exchange-cancel-btn"}
                                appearance="secondary"
                                shape={"circular"}
                                disabled={ending.ending}
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
