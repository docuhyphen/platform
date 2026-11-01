import {ExchangeDetailedDto} from "../../../models/models.tsx";
import React from "react";
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
    MessageBar,
    MessageBarBody,
    Spinner,
    Text
} from "@fluentui/react-components";
import {useExchangeEditDialogStyles} from "./ExchangeEditDialogStyles.tsx";
import {useExchangeEdit} from "./useExchangeEdit.ts";
import ExchangeEditFormFields from "./exchange-edit-form-fields/ExchangeEditFormFields.tsx";

interface ExchangeEditDialogProps
{
    isOpen: boolean;
    onDismiss: () => void;
    exchange: ExchangeDetailedDto;
    onExchangeEdited: (exchange: ExchangeDetailedDto) => void;
}

const ExchangeEditDialog: React.FC<ExchangeEditDialogProps> = (
    {
        isOpen,
        onDismiss,
        exchange,
        onExchangeEdited
    }) =>
{
    const styles = useExchangeEditDialogStyles();
    const globalStyles = useGlobalStyles();
    const edit = useExchangeEdit(exchange, onExchangeEdited, onDismiss);

    return (
        <Dialog modalType="alert"
                open={isOpen}>
            <DialogSurface id={"exchange-edit-dialog-dialog-surface-1"}>
                <DialogBody id={"exchange-edit-dialog-dialog-body-1"}>
                    <DialogTitle id={"exchange-edit-dialog-dialog-title-1"}>Edit {exchange && exchange.name}</DialogTitle>
                    <DialogContent id={"exchange-edit-dialog-dialog-content-1"}
                                   className={styles.dialogContentContainer}>
                        {edit.dialogErrorMessage && (
                            <MessageBar id={"exchange-edit-dialog-message-bar-1"}
                                        intent="error">
                                <MessageBarBody id={"exchange-edit-dialog-message-bar-body-1"}>
                                    <Text id={"exchange-edit-dialog-text-1"}
                                          size={200}>{edit.dialogErrorMessage}</Text>
                                </MessageBarBody>
                            </MessageBar>
                        )}
                        <ExchangeEditFormFields name={edit.name}
                                                description={edit.description}
                                                onNameChange={edit.setName}
                                                onDescriptionChange={edit.setDescription}/>
                    </DialogContent>
                    <DialogActions id={"exchange-edit-dialog-dialog-actions-1"}>
                        <Button id={"edit-exchange-submit-btn"}
                                appearance="primary"
                                className={globalStyles.buttonWithLoading}
                                shape={"circular"}
                                onClick={edit.onEdit}>
                            {edit.editingExchange && <Spinner id={"exchange-edit-dialog-spinner-1"}
                                                              size={"tiny"}/>}
                            Edit
                        </Button>
                        <DialogTrigger disableButtonEnhancement>
                            <Button id={"edit-exchange-cancel-btn"}
                                    appearance="secondary"
                                    shape={"circular"}
                                    disabled={edit.editingExchange}
                                    onClick={edit.onCancel}>
                                Cancel
                            </Button>
                        </DialogTrigger>
                    </DialogActions>
                </DialogBody>
            </DialogSurface>
        </Dialog>
    );
};

export default ExchangeEditDialog;
