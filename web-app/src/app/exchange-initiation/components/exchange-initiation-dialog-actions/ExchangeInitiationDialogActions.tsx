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
                <Button appearance="primary"
                        onClick={onResetInitiation}
                        shape={"circular"}>
                    Create another
                </Button>
            )}

            {(!choosingBlueprint && !exchangeInitiatedSuccessfully) && (
                <Button
                    onClick={onInitiateExchange}
                    appearance="primary"
                    shape="circular"
                    className={styles.buttonWithLoading}>
                    {!initiatingExchange ? (requestingDocuments ? "Request Documents" : "Send Documents") : (
                        <>
                            <Spinner size="tiny"/> Starting Exchange
                        </>
                    )}
                </Button>
            )}

            <Button
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