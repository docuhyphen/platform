import React from 'react';
import {Button, DialogTrigger, Spinner} from "@fluentui/react-components";
import {useGlobalStyles} from "../../../GlobalStyles.tsx";

interface DialogActionsProps
{
    initiatingSession: boolean;
    sessionInitiatedSuccessfully: boolean;
    choosingTemplate: boolean;
    onCancelInitiation: () => void;
    onInitiateSession: () => void;
}

const SessionDialogActions: React.FC<DialogActionsProps> = ({
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
                <Button appearance="primary" onClick={onCancelInitiation}>
                    Create Another
                </Button>
            )}

            {(!choosingTemplate && !sessionInitiatedSuccessfully) && (
                <Button
                    onClick={onInitiateSession}
                    appearance="primary"
                    shape="circular"
                    className={styles.buttonWithLoading}
                >
                    {!initiatingSession ? "Start Session" : (
                        <>
                            <Spinner size="extra-small"/> Starting Session
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

export default SessionDialogActions;