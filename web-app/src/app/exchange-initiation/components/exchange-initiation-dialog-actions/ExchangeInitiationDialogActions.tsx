import React from 'react';
import {Button, Spinner} from "@fluentui/react-components";
import {useGlobalStyles} from "../../../../GlobalStyles.tsx";

interface ExchangeInitiationDialogActionsProps
{
    requestingDocuments: boolean;
    initiatingExchange: boolean;
    exchangeInitiatedSuccessfully: boolean;
    choosingTemplate: boolean;
    onResetInitiation: () => void;
    onCloseDialog: () => void;
    onInitiateExchange: () => void;
}

const ExchangeInitiationDialogActions: React.FC<ExchangeInitiationDialogActionsProps> = (
    {
        requestingDocuments,
        initiatingExchange,
        exchangeInitiatedSuccessfully,
        choosingTemplate,
        onResetInitiation,
        onCloseDialog,
        onInitiateExchange
    }) =>
{

    const styles = useGlobalStyles()

    return (
        <>

            {(!choosingTemplate && exchangeInitiatedSuccessfully) && (
                <Button appearance="primary"
                        onClick={onResetInitiation}
                        shape={"circular"}>
                    Create another
                </Button>
            )}

            {(!choosingTemplate && !exchangeInitiatedSuccessfully) && (
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
                {(!choosingTemplate && exchangeInitiatedSuccessfully) ? "Close" : "Cancel"}
            </Button>
        </>
    );
};

export default ExchangeInitiationDialogActions;