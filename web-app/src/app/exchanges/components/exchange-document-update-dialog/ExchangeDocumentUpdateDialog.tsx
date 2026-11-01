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
    Text,
} from "@fluentui/react-components";
import {useExchangeDocumentUpdateDialogStyles} from "./ExchangeDocumentUpdateDialogStyles.tsx";
import {useExchangeDocumentUpdateDialog} from "./useExchangeDocumentUpdateDialog.ts";
import DocumentRestrictionControls
    from "../exchange-document-add-dialog/document-restriction-controls/DocumentRestrictionControls.tsx";

interface UpdateDocumentDialogProps
{
    isOpen: boolean;
    exchangeId: string;
    exchangeDocument: DocumentDetailedDto;
    onDismiss: () => void;
    onDocumentUpdated: (document: DocumentDetailedDto) => void;
}

const ExchangeDocumentUpdateDialog: React.FC<UpdateDocumentDialogProps> = (
    {
        isOpen,
        exchangeId,
        exchangeDocument,
        onDismiss,
        onDocumentUpdated,
    }) =>
{
    const globalStyles = useGlobalStyles();
    const styles = useExchangeDocumentUpdateDialogStyles();
    const {
        documentTitle,
        setDocumentTitle,
        isRestrictionEnabled,
        setIsRestrictionEnabled,
        selectedRestrictionType,
        updatingDocument,
        dialogErrorMessage,
        handleDismiss,
        handleUpdateDocument,
        handleOptionSelect
    } = useExchangeDocumentUpdateDialog(exchangeId, exchangeDocument, onDismiss, onDocumentUpdated);

    return (
        <Dialog modalType="alert"
                open={isOpen}>
            <DialogSurface id={"exchange-document-update-dialog-dialog-surface-1"}>
                <DialogBody id={"exchange-document-update-dialog-dialog-body-1"}>
                    <DialogTitle id={"exchange-document-update-dialog-dialog-title-1"}>Update Exchange document</DialogTitle>
                    <DialogContent id={"exchange-document-update-dialog-dialog-content-1"}>
                        {dialogErrorMessage && (
                            <MessageBar id={"exchange-document-update-dialog-message-bar-1"}
                                        intent="error">
                                <MessageBarBody id={"exchange-document-update-dialog-message-bar-body-1"}>
                                    <Text id={"exchange-document-update-dialog-text-1"}
                                          size={200}>{dialogErrorMessage}</Text>
                                </MessageBarBody>
                            </MessageBar>
                        )}
                        <Field id={"exchange-document-update-dialog-field-1"}
                               className={styles.documentTitleField}
                               label="New document name">
                            <Input id={"input-update-document-title"}
                                   type="text"
                                   value={documentTitle}
                                   required
                                   onChange={(e) => setDocumentTitle(e.target.value)}/>
                        </Field>
                        <DocumentRestrictionControls idPrefix={"exchange-document-update-dialog"}
                                                     switchId={"switch-update-document-restrict-type"}
                                                     dropdownId={"dropdown-update-document-type"}
                                                     restrictionEnabled={isRestrictionEnabled}
                                                     selectedType={selectedRestrictionType}
                                                     onRestrictionToggle={setIsRestrictionEnabled}
                                                     onOptionSelect={handleOptionSelect}/>
                    </DialogContent>
                    <DialogActions id={"exchange-document-update-dialog-dialog-actions-1"}>
                        <Button id="update-document-submit-btn"
                                appearance="primary"
                                className={globalStyles.buttonWithLoading}
                                shape="circular"
                                onClick={handleUpdateDocument}>
                            {updatingDocument && <Spinner id={"exchange-document-update-dialog-spinner-1"}
                                                          size="tiny"/>}
                            Update
                        </Button>
                        <DialogTrigger disableButtonEnhancement>
                            <Button id="update-document-close-btn"
                                    appearance="secondary"
                                    onClick={handleDismiss}
                                    shape="circular"
                                    disabled={updatingDocument}>
                                Close
                            </Button>
                        </DialogTrigger>
                    </DialogActions>
                </DialogBody>
            </DialogSurface>
        </Dialog>
    );
};

export default ExchangeDocumentUpdateDialog;
