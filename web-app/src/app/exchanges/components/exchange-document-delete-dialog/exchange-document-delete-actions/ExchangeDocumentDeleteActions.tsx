import React from "react";
import {Button, DialogTrigger, Spinner} from "@fluentui/react-components";
import {useGlobalStyles} from "../../../../../GlobalStyles.tsx";

interface ExchangeDocumentDeleteActionsProps
{
    deleteStarted: boolean;
    deletingDocument: boolean;
    onDelete: () => void;
    onCancel: () => void;
    onDismiss: () => void;
}

const ExchangeDocumentDeleteActions: React.FC<ExchangeDocumentDeleteActionsProps> = (
    {
        deleteStarted,
        deletingDocument,
        onDelete,
        onCancel,
        onDismiss
    }) =>
{
    const globalStyles = useGlobalStyles();

    if (deleteStarted)
    {
        return (
            <Button id="document-delete-countdown-cancel-btn"
                    appearance="primary"
                    shape="circular"
                    onClick={onCancel}>
                Cancel
            </Button>
        );
    }

    return (
        <>
            <Button id="document-delete-confirm-btn"
                    appearance="primary"
                    className={globalStyles.buttonWithLoading}
                    shape="circular"
                    onClick={onDelete}>
                {deletingDocument && <Spinner id={"exchange-document-delete-dialog-spinner-1"}
                                              size="tiny"/>}
                Yes, Delete
            </Button>
            <DialogTrigger disableButtonEnhancement>
                <Button id="document-delete-cancel-btn"
                        appearance="secondary"
                        shape="circular"
                        disabled={deletingDocument}
                        onClick={onDismiss}>
                    No, Cancel
                </Button>
            </DialogTrigger>
        </>
    );
};

export default ExchangeDocumentDeleteActions;
