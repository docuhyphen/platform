import {useEffect, useRef, useState} from "react";
import {WorkflowDefinitionSummaryDto} from "../../../models/models.tsx";
import {deleteWorkflowDefinition} from "../../../../services/workflowService.ts";

const COUNTDOWN_SECONDS = 10;

interface Options
{
    isOpen: boolean;
    definition: WorkflowDefinitionSummaryDto;
    onDeleted: () => void;
    onDismiss: () => void;
}

export const useWorkflowDeletion = ({isOpen, definition, onDeleted, onDismiss}: Options) =>
{
    const [deleting, setDeleting] = useState(false);
    const [deleteStarted, setDeleteStarted] = useState(false);
    const [countdown, setCountdown] = useState(COUNTDOWN_SECONDS);
    const [errorMessage, setErrorMessage] = useState<string | null>(null);
    const timerRef = useRef<ReturnType<typeof setInterval> | null>(null);

    useEffect(() =>
    {
        if (isOpen) setErrorMessage(null);
    }, [isOpen]);

    const completeDeletion = async () =>
    {
        setDeleting(true);
        try
        {
            await deleteWorkflowDefinition(definition.id);
            onDeleted();
            onDismiss();
        }
        catch (error)
        {
            setErrorMessage("Error deleting workflow");
            console.error("Error deleting workflow:", error);
        }
        finally
        {
            setDeleting(false);
            setDeleteStarted(false);
        }
    };

    const startDeleteCountdown = () =>
    {
        setDeleteStarted(true);
        setCountdown(COUNTDOWN_SECONDS);

        timerRef.current = setInterval(() =>
        {
            setCountdown(prev =>
            {
                if (prev <= 1)
                {
                    if (timerRef.current) clearInterval(timerRef.current);
                    completeDeletion();
                    return 0;
                }
                return prev - 1;
            });
        }, 1000);
    };

    const cancelDeleteCountdown = () =>
    {
        if (timerRef.current)
        {
            clearInterval(timerRef.current);
        }
        setDeleteStarted(false);
        setCountdown(COUNTDOWN_SECONDS);
    };

    return {deleting, deleteStarted, countdown, errorMessage, startDeleteCountdown, cancelDeleteCountdown};
};
