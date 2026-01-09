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
import {deleteOrganizationGroup, OrganizationGroupBasicDto} from "../../../../services/organizationApi.ts";

interface GroupDeleteDialog
{
    organizationId: string,
    isOpen: boolean;
    onDismiss: () => void;
    group: OrganizationGroupBasicDto;
    onDeleted: (groupId: string) => void;
}

const GroupDeleteDialog: React.FC<GroupDeleteDialog> = (
    {
        organizationId,
        isOpen,
        onDismiss,
        group,
        onDeleted
    }) =>
{
    const token = useToken();
    const [deletingSession, setDeletingSession] = React.useState(false);
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

        setDeletingSession(true)

        try
        {
            await deleteOrganizationGroup(organizationId, group.id, token || undefined);
            onDeleted(group.id);
            onDismiss();
        }
        catch (error)
        {
            alert("Error deleting document");
            console.error("Error deleting document:", error);
        }
        finally
        {
            setDeletingSession(false);
            setDeleteStarted(false);
        }
    }

    return <>
        {<Dialog modalType="alert" open={isOpen}>
            <DialogSurface>
                <DialogBody>
                    <DialogTitle>Deleting {group && group.name}</DialogTitle>
                    <DialogContent>
                        {deleteStarted ? (
                            <div>
                                Deleting in {countdown} seconds...
                            </div>
                        ) : (
                            <div>
                                Are you sure you want to delete this group?
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
                                    {deletingSession && <Spinner size={"tiny"}/>}
                                    Yes, Delete
                                </Button>
                                <DialogTrigger disableButtonEnhancement>
                                    <Button appearance="secondary"
                                            shape={"circular"}
                                            disabled={deletingSession}
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

export default GroupDeleteDialog;