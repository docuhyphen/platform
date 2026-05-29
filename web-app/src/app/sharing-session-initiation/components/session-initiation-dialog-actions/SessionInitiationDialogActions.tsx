import React from 'react';
import {Button, Spinner} from "@fluentui/react-components";
import {useGlobalStyles} from "../../../../GlobalStyles.tsx";

interface SessionInitiationDialogActionsProps
{
    requestingDocuments: boolean;
    initiatingSession: boolean;
    sessionInitiatedSuccessfully: boolean;
    choosingTemplate: boolean;
    onResetInitiation: () => void;
    onCloseDialog: () => void;
    onInitiateSession: () => void;
}

const SessionInitiationDialogActions: React.FC<SessionInitiationDialogActionsProps> = (
    {
        requestingDocuments,
        initiatingSession,
        sessionInitiatedSuccessfully,
        choosingTemplate,
        onResetInitiation,
        onCloseDialog,
        onInitiateSession
    }) =>
{

    const styles = useGlobalStyles()

    return (
        <>

            {(!choosingTemplate && sessionInitiatedSuccessfully) && (
                <Button appearance="primary"
                        onClick={onResetInitiation}
                        shape={"circular"}>
                    Create another
                </Button>
            )}

            {(!choosingTemplate && !sessionInitiatedSuccessfully) && (
                <Button
                    onClick={onInitiateSession}
                    appearance="primary"
                    shape="circular"
                    className={styles.buttonWithLoading}>
                    {!initiatingSession ? (requestingDocuments ? "Request Documents" : "Send Documents") : (
                        <>
                            <Spinner size="tiny"/> Starting Session
                        </>
                    )}
                </Button>
            )}

            <Button
                shape={"circular"}
                disabled={initiatingSession}
                onClick={onCloseDialog}
            >
                {(!choosingTemplate && sessionInitiatedSuccessfully) ? "Close" : "Cancel"}
            </Button>
        </>
    );
};

export default SessionInitiationDialogActions;