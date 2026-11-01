import {DocumentDetailedDto} from "../../../models/models.tsx";
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
    Field,
    Input,
    MessageBar,
    MessageBarBody,
    Spinner,
    Text
} from "@fluentui/react-components";
import {useExchangeDocumentAddDialogStyles} from "./ExchangeDocumentAddDialogStyles.tsx";
import {useExchangeDocumentAddDialog} from "./useExchangeDocumentAddDialog.ts";
import DocumentRestrictionControls from "./document-restriction-controls/DocumentRestrictionControls.tsx";

interface AddDocumentDialogProps
{
    isOpen: boolean;
    exchangeId: string,
    onDismiss: () => void;
    onDocumentAdded: (document: DocumentDetailedDto) => void;
}

const AddDocumentDialog: React.FC<AddDocumentDialogProps> = (
    {
        isOpen,
        onDocumentAdded,
        onDismiss,
        exchangeId
    }) =>
{
    const globalStyles = useGlobalStyles();
    const styles = useExchangeDocumentAddDialogStyles();
    const {
        documentTitle,
        setDocumentTitle,
        restrictType,
        setRestrictType,
        restrictedType,
        addingDocument,
        dialogErrorMessage,
        onDismissDialog,
        onAddDocument,
        onOptionSelected
    } = useExchangeDocumentAddDialog(isOpen, exchangeId, onDismiss, onDocumentAdded);

    return (
        <Dialog modalType="alert"
                open={isOpen}>
            <DialogSurface id={"exchange-document-add-dialog-dialog-surface-1"}>
                <DialogBody id={"exchange-document-add-dialog-dialog-body-1"}>
                    <DialogTitle id={"exchange-document-add-dialog-dialog-title-1"}>Add Exchange document</DialogTitle>
                    <DialogContent id={"exchange-document-add-dialog-dialog-content-1"}
                                   className={styles.dialogContentContainer}>
                        {dialogErrorMessage && (
                            <MessageBar id={"exchange-document-add-dialog-message-bar-1"}
                                        intent="error">
                                <MessageBarBody id={"exchange-document-add-dialog-message-bar-body-1"}>
                                    <Text id={"exchange-document-add-dialog-text-1"}
                                          size={200}>{dialogErrorMessage}</Text>
                                </MessageBarBody>
                            </MessageBar>
                        )}
                        <Field id={"exchange-document-add-dialog-field-1"}
                               className={styles.documentTitleField}>
                            <Input id={"input-add-document-title"}
                                   type="text"
                                   value={documentTitle}
                                   required
                                   onChange={(e) => setDocumentTitle(e.target.value)}
                                   placeholder="Document name"/>
                        </Field>
                        <DocumentRestrictionControls idPrefix={"exchange-document-add-dialog"}
                                                     switchId={"switch-add-document-restrict-type"}
                                                     dropdownId={"dropdown-add-document-type"}
                                                     restrictionEnabled={restrictType}
                                                     selectedType={restrictedType}
                                                     onRestrictionToggle={setRestrictType}
                                                     onOptionSelect={onOptionSelected}/>
                    </DialogContent>
                    <DialogActions id={"exchange-document-add-dialog-dialog-actions-1"}>
                        <Button id={"add-document-submit-btn"}
                                appearance="primary"
                                shape={"circular"}
                                className={globalStyles.buttonWithLoading}
                                onClick={onAddDocument}>
                            {addingDocument && <Spinner id={"exchange-document-add-dialog-spinner-1"}
                                                        size={"tiny"}/>}
                            Add
                        </Button>
                        <DialogTrigger disableButtonEnhancement>
                            <Button id={"add-document-close-btn"}
                                    appearance="secondary"
                                    onClick={onDismissDialog}
                                    shape={"circular"}
                                    disabled={addingDocument}>
                                Close
                            </Button>
                        </DialogTrigger>
                    </DialogActions>
                </DialogBody>
            </DialogSurface>
        </Dialog>
    );
}

export default AddDocumentDialog;
