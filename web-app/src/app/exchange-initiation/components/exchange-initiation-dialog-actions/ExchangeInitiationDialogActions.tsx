import React from 'react';
import {Button, Spinner} from "@fluentui/react-components";
import {useGlobalStyles} from "../../../../GlobalStyles.tsx";

interface ExchangeInitiationDialogActionsProps
{
    requestingDocuments: boolean;
    initiatingExchange: boolean;
    exchangeInitiatedSuccessfully: boolean;
    choosingBlueprint: boolean;
    onResetInitiation: () => void;
    onCloseDialog: () => void;
    onInitiateExchange: () => void;
}

const ExchangeInitiationDialogActions: React.FC<ExchangeInitiationDialogActionsProps> = (
    {
        requestingDocuments,
        initiatingExchange,
        exchangeInitiatedSuccessfully,
        choosingBlueprint,
        onResetInitiation,
        onCloseDialog,
        onInitiateExchange,
    }) =>
{

    const styles = useGlobalStyles()

    return (
        <>

            {(!choosingBlueprint && exchangeInitiatedSuccessfully) && (
                <Button
                    id={"exchange-create-another-btn"}
                    appearance="primary"
                    onClick={onResetInitiation}
                    shape={"circular"}>
                    Create another
                </Button>
            )}

            {(!choosingBlueprint && !exchangeInitiatedSuccessfully) && (
                <Button
                    id={"exchange-initiate-btn"}
                    onClick={onInitiateExchange}
                    appearance="primary"
                    shape="circular"
                    className={styles.buttonWithLoading}>
                    {!initiatingExchange ? (requestingDocuments ? "Start Exchange" : "Start Exchange") : (
                        <>
                            <Spinner id={"exchange-initiation-dialog-actions-spinner-1"}
                                     size="tiny"/> Starting
                        </>
                    )}
                </Button>
            )}

            <Button
                id={"exchange-cancel-btn"}
                shape={"circular"}
                disabled={initiatingExchange}
                onClick={onCloseDialog}
            >
                {(!choosingBlueprint && exchangeInitiatedSuccessfully) ? "Close" : "Cancel"}
            </Button>
        </>
    );
};

export default ExchangeInitiationDialogActions;