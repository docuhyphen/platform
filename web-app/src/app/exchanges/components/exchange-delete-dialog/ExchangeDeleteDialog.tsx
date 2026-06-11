import {ExchangeDetailedDto} from "../../../models/models.tsx";
import React from "react";
import useToken from "../../../../context/useToken.tsx";
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
    Spinner
} from "@fluentui/react-components";
import {deleteExchange} from "../../../../services/exchangeApi.ts";
import {publishExchangeDelete} from "../../../observable/exchangeObservables.ts";

interface ExchangeDeleteDialogProps
{
    isOpen: boolean;
    onDismiss: () => void;
    exchange: ExchangeDetailedDto;
    onExchangeDeleted: (exchangeId: string) => void;
}

const ExchangeDeleteDialog: React.FC<ExchangeDeleteDialogProps> = (
    {
        isOpen,
        onDismiss,
        exchange,
        onExchangeDeleted
    }) =>
{

    const token = useToken();
    const [deletingExchange, setDeletingExchange] = React.useState(false);
    const globalStyles = useGlobalStyles()
    const [deleteStarted, setDeleteStarted] = React.useState(false);
    const [countdown, setCountdown] = React.useState(10);
    const timerRef = React.useRef<NodeJS.Timeout | null>(null);

    const onDelete = () =>
    {
        setDeleteStarted(true);
        setCountdown(10);

        timerRef.current = setInterval(() =>
        {
            setCountdown(prevCountdown =>
            {
                if (prevCountdown <= 1)
                {
                    clearInterval(timerRef.current!);
                    completeDeletion();
                    return 0;
                }
                return prevCountdown - 1;
            });
        }, 1000);
    }

    const onCancel = () =>
    {
        if (timerRef.current)
        {
            clearInterval(timerRef.current);
        }
        setDeleteStarted(false);
        setCountdown(5);
    }

    const completeDeletion = async () =>
    {

        setDeletingExchange(true)

        try
        {
            await deleteExchange(exchange.id);
            onExchangeDeleted(exchange.id);
            publishExchangeDelete(exchange.id);
            onDismiss();
        }
        catch (error)
        {
            alert("Error deleting document");
            console.error("Error deleting document:", error);
        }
        finally
        {
            setDeletingExchange(false);
            setDeleteStarted(false);
        }
    }

    return <>
        {<Dialog modalType="alert" open={isOpen}>
            <DialogSurface>
                <DialogBody>
                    <DialogTitle>Deleting {exchange && exchange.name}</DialogTitle>
                    <DialogContent>
                        {deleteStarted ? (
                            <div>
                                Deleting in {countdown} seconds...
                            </div>
                        ) : (
                            <div>
                                Are you sure you want to delete this Exchange?
                            </div>
                        )}
                    </DialogContent>
                    <DialogActions>
                        {deleteStarted ? (
                            <Button appearance="primary"
                                    className={globalStyles.buttonWithLoading}
                                    shape={"circular"}
                                    onClick={onCancel}>
                                Cancel
                            </Button>
                        ) : (
                            <>
                                <Button appearance="primary"
                                        className={globalStyles.buttonWithLoading}
                                        shape={"circular"}
                                        onClick={onDelete}>
                                    {deletingExchange && <Spinner size={"tiny"}/>}
                                    Yes, Delete
                                </Button>
                                <DialogTrigger disableButtonEnhancement>
                                    <Button appearance="secondary"
                                            shape={"circular"}
                                            disabled={deletingExchange}
                                            onClick={onDismiss}>
                                        No, Cancel
                                    </Button>
                                </DialogTrigger>
                            </>
                        )}
                    </DialogActions>
                </DialogBody>
            </DialogSurface>
        </Dialog>
        }
    </>
}

export default ExchangeDeleteDialog;