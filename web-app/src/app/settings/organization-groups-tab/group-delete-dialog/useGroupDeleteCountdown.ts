import {useRef, useState} from "react";
import useToken from "../../../../context/useToken.tsx";
import {deleteOrganizationGroup} from "../../../../services/organizationApi.ts";
import {OrganizationGroupDetailedDto} from "../../../models/models.tsx";
import {extractDialogErrorMessage} from "../../components/dialogErrorMessage.ts";

interface GroupDeleteCountdownOptions
{
    organizationId: string;
    group: OrganizationGroupDetailedDto | null;
    onDeleted: (groupId: string) => void;
    onDismiss: () => void;
}

const DELETE_FAILURE_MESSAGE = "Something went wrong while deleting the group. Please try again in a moment.";

export const useGroupDeleteCountdown = ({organizationId, group, onDeleted, onDismiss}: GroupDeleteCountdownOptions) =>
{
    const token = useToken();
    const [deleting, setDeleting] = useState(false);
    const [deleteStarted, setDeleteStarted] = useState(false);
    const [countdown, setCountdown] = useState(10);
    const [error, setError] = useState<string | null>(null);
    const timerRef = useRef<ReturnType<typeof setInterval> | null>(null);

    const completeDeletion = async () =>
    {
        if (!organizationId || !group?.id)
        {
            setError("Cannot delete: missing organization or group information. Please reload the page.");
            setDeleteStarted(false);
            return;
        }

        setDeleting(true);
        setError(null);

        try
        {
            await deleteOrganizationGroup(organizationId, group.id, token || undefined);
            onDeleted(group.id);
            onDismiss();
        }
        catch (e)
        {
            setError(extractDialogErrorMessage(e, DELETE_FAILURE_MESSAGE));
            console.error("Error deleting group:", e);
        }
        finally
        {
            setDeleting(false);
            setDeleteStarted(false);
        }
    };

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
    };

    const onCancel = () =>
    {
        if (timerRef.current)
        {
            clearInterval(timerRef.current);
        }
        setDeleteStarted(false);
        setCountdown(5);
    };

    return {deleting, deleteStarted, countdown, error, onDelete, onCancel};
};
