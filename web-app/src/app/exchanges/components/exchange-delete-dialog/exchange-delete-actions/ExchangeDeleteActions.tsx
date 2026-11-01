import React from "react";
import {Button, DialogTrigger, Spinner} from "@fluentui/react-components";
import {useGlobalStyles} from "../../../../../GlobalStyles.tsx";

interface ExchangeDeleteActionsProps
{
    deleteStarted: boolean;
    deletingExchange: boolean;
    onDelete: () => void;
    onCancel: () => void;
    onDismiss: () => void;
}

const ExchangeDeleteActions: React.FC<ExchangeDeleteActionsProps> = (
    {
        deleteStarted,
        deletingExchange,
        onDelete,
        onCancel,
        onDismiss
    }) =>
{
    const globalStyles = useGlobalStyles();

    if (deleteStarted)
    {
        return (
            <Button id="exchange-delete-countdown-cancel-btn"
                    appearance="primary"
                    className={globalStyles.buttonWithLoading}
                    shape={"circular"}
                    onClick={onCancel}>
                Cancel
            </Button>
        );
    }

    return (
        <>
            <Button id="exchange-delete-confirm-btn"
                    appearance="primary"
                    className={globalStyles.buttonWithLoading}
                    shape={"circular"}
                    onClick={onDelete}>
                {deletingExchange && <Spinner id={"exchange-delete-dialog-spinner-1"}
                                              size={"tiny"}/>}
                Yes, Delete
            </Button>
            <DialogTrigger disableButtonEnhancement>
                <Button id="exchange-delete-cancel-btn"
                        appearance="secondary"
                        shape={"circular"}
                        disabled={deletingExchange}
                        onClick={onDismiss}>
                    No, Cancel
                </Button>
            </DialogTrigger>
        </>
    );
};

export default ExchangeDeleteActions;
