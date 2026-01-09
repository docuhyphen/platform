import React from 'react';
import {Button, DialogTrigger, Spinner} from "@fluentui/react-components";
import {useGlobalStyles} from "../../../../GlobalStyles.tsx";

interface SessionInitiationDialogActionsProps
{
    requestingDocuments: boolean;
    initiatingSession: boolean;
    sessionInitiatedSuccessfully: boolean;
    choosingTemplate: boolean;
    onCancelInitiation: () => void;
    onInitiateSession: () => void;
}

const SessionInitiationDialogActions: React.FC<SessionInitiationDialogActionsProps> = (
    {
        requestingDocuments,
        initiatingSession,
        sessionInitiatedSuccessfully,
        choosingTemplate,
        onCancelInitiation,
        onInitiateSession
    }) =>
{

    const styles = useGlobalStyles()

    return (
        <>

            {(!choosingTemplate && sessionInitiatedSuccessfully) && (
                <Button appearance="primary"
                        onClick={onCancelInitiation}
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

            <DialogTrigger>
                <Button
                    shape={"circular"}
                    disabled={initiatingSession}
                    onClick={onCancelInitiation}
                >
                    {(!choosingTemplate && sessionInitiatedSuccessfully) ? "Close" : "Cancel"}
                </Button>
            </DialogTrigger>
        </>
    );
};

export default SessionInitiationDialogActions;