import {ExchangeDetailedDto} from "../../../models/models.tsx";
import React from "react";
import {
    Dialog,
    DialogActions,
    DialogBody,
    DialogContent,
    DialogSurface,
    DialogTitle,
    MessageBar,
    MessageBarBody,
    Text
} from "@fluentui/react-components";
import {useExchangeDeletion} from "./useExchangeDeletion.ts";
import ExchangeDeleteActions from "./exchange-delete-actions/ExchangeDeleteActions.tsx";

interface ExchangeDeleteDialogProps
{
    isOpen: boolean;
    onDismiss: () => void;
    exchange: ExchangeDetailedDto;
    onExchangeDeleted: (exchangeId: string) => void;
}

const ExchangeDeleteDialog: React.FC<ExchangeDeleteDialogProps> = (
    {
        isOpen,
        onDismiss,
        exchange,
        onExchangeDeleted
    }) =>
{
    const deletion = useExchangeDeletion(isOpen, exchange, onExchangeDeleted, onDismiss);

    return (
        <Dialog modalType="alert"
                open={isOpen}>
            <DialogSurface id={"exchange-delete-dialog-dialog-surface-1"}>
                <DialogBody id={"exchange-delete-dialog-dialog-body-1"}>
                    <DialogTitle id={"exchange-delete-dialog-dialog-title-1"}>Deleting {exchange && exchange.name}</DialogTitle>
                    <DialogContent id={"exchange-delete-dialog-dialog-content-1"}>
                        {deletion.dialogErrorMessage && (
                            <MessageBar id={"exchange-delete-dialog-message-bar-1"}
                                        intent="error">
                                <MessageBarBody id={"exchange-delete-dialog-message-bar-body-1"}>
                                    <Text id={"exchange-delete-dialog-text-1"}
                                          size={200}>{deletion.dialogErrorMessage}</Text>
                                </MessageBarBody>
                            </MessageBar>
                        )}
                        {deletion.deleteStarted ? (
                            <div id={"exchange-delete-dialog-div-1"}>
                                Deleting in {deletion.countdown} seconds...
                            </div>
                        ) : (
                            <div id={"exchange-delete-dialog-div-2"}>
                                Are you sure you want to delete this Exchange?
                            </div>
                        )}
                    </DialogContent>
                    <DialogActions id={"exchange-delete-dialog-dialog-actions-1"}>
                        <ExchangeDeleteActions deleteStarted={deletion.deleteStarted}
                                               deletingExchange={deletion.deletingExchange}
                                               onDelete={deletion.onDelete}
                                               onCancel={deletion.onCancel}
                                               onDismiss={onDismiss}/>
                    </DialogActions>
                </DialogBody>
            </DialogSurface>
        </Dialog>
    );
};

export default ExchangeDeleteDialog;
