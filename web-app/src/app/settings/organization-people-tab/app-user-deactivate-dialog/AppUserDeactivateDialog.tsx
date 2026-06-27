import React, {useEffect} from "react";
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
    DialogTrigger, MessageBar, MessageBarActions, MessageBarBody,
    Spinner, Text
} from "@fluentui/react-components";
import {
    AppUserDetailedDto,
    ResponseError
} from "../../../models/models.tsx";
import apiClient from '../../../../services/apiClient';
import {
    checkAppUserIsDeletable,
    deleteOrganizationAppUser,
    updateOrganizationUser
} from "../../../../services/organizationApi.ts";
import {DismissRegular} from "@fluentui/react-icons";
import {useAppUserDeactivateDialogStyles} from "./AppUserDeactivateDialogStyles.tsx";

interface AppUserDeactivateDialogProps
{
    isOpen: boolean;
    onDismiss: () => void;
    appUser: AppUserDetailedDto
    organizationId: string
    onDeactivated: (appUserId: string) => void;
    onDeleted: (appUserId: string) => void;
}

const AppUserDeactivateDialog: React.FC<AppUserDeactivateDialogProps> = (
    {
        isOpen,
        onDismiss,
        appUser,
        organizationId,
        onDeactivated,
        onDeleted
    }) =>
{
    const styles = useAppUserDeactivateDialogStyles()
    const token = useToken();
    const [deactivating, setDeactivating] = React.useState(false);
    const [deleting, setDeleting] = React.useState(false);
    const [canBeDeleted, setCanBeDeleted] = React.useState(false);
    const [dialogErrorMessage, setDialogErrorMessage] = React.useState<string | null>(null);
    const [appUserDeletionMessage, setAppUserDeletionMessage] = React.useState<string | null>(null);
    const globalStyles = useGlobalStyles();

    useEffect(() =>
    {
        if (isOpen && appUser?.id)
        {
            doAppUserIsDeletableCheck();
        }
        else
        {
            setCanBeDeleted(false);
            setAppUserDeletionMessage(null);
            setDialogErrorMessage(null);
            setDeactivating(false);
            setDeleting(false);
        }
    }, [isOpen, appUser, organizationId]);

    const doAppUserIsDeletableCheck = async () =>
    {
        try
        {
            await checkAppUserIsDeletable(organizationId, appUser.id!, token!);
            setCanBeDeleted(true);
        }
        catch (error)
        {
            setCanBeDeleted(false);

            if ((error as any).response?.status === 409)
            {
                setAppUserDeletionMessage(
                    "This app user cannot be deleted because they are associated with existing data. Please deactivate instead."
                );
            }
        }
    };

    const onDelete = async () =>
    {
        if (!canBeDeleted)
        {
            onDeactivate();
            return;
        }

        setDeleting(true);

        if (deleting)
        {
            return;
        }

        try
        {
            await deleteOrganizationAppUser(organizationId, appUser.id!, token!);
            onDeleted(appUser.id!);
        }
        catch (error: ResponseError | any)
        {
            const errorMessage = ((error as ResponseError)?.errorMessage);

            if (error.response?.status === 409)
            {
                setAppUserDeletionMessage(errorMessage ||
                    "This app user cannot be deleted because they are associated with existing " +
                    "data. Please deactivate instead.");
                return;
            }

            setDialogErrorMessage(errorMessage || "An unexpected error occurred while deleting the app user.");
        }
        finally
        {
            setDeleting(false);
        }
    };

    const onDeactivate = async () =>
    {
        setDeactivating(true);

        try
        {
            await updateOrganizationUser(
                organizationId,
                appUser.id.toString(),
                {
                    role: appUser.role,
                    isActive: false,
                    person: {
                        firstName: appUser.person.firstName!,
                        lastName: appUser.person.lastName!
                    }
                },
                token || undefined
            );

            onDeactivated(appUser.id!);
        }
        catch (err: any)
        {
            console.error("Failed to deactivate user:", err);
        }
        finally
        {
            setDeactivating(false);
        }
    };

    const renderDialogError = () => (
        dialogErrorMessage && (
            <MessageBar intent={"error"}>
                <MessageBarBody>
                    <Text size={200}> {dialogErrorMessage} </Text>
                </MessageBarBody>
                <MessageBarActions
                    containerAction={
                        <Button
                            id={"deactivate-dialog-dismiss-error-btn"}
                            onClick={() => setDialogErrorMessage(null)}
                            appearance="transparent"
                            shape={"circular"}
                            icon={<DismissRegular/>}
                        />
                    }
                />
            </MessageBar>
        )
    );

    return <>
        {<Dialog modalType="alert" open={isOpen}>
            <DialogSurface>
                <DialogBody>
                    <DialogTitle>
                        {canBeDeleted ? "Deleting" : "Deactivating"} {appUser && `${appUser.person.firstName} ${appUser.person.lastName}`}
                    </DialogTitle>
                    <DialogContent className={styles.dialogContentContainer}>
                        {renderDialogError()}
                        {appUserDeletionMessage ? appUserDeletionMessage :
                            canBeDeleted ? "Are you sure you want to delete this user? This action cannot be undone." :
                                "Are you sure you want to deactivate this user?"}
                    </DialogContent>
                    <DialogActions>
                        <Button
                            id={"deactivate-dialog-confirm-btn"}
                            appearance="primary"
                            className={globalStyles.buttonWithLoading}
                            shape={"circular"}
                            onClick={() => onDelete()}>
                            {(deactivating || deleting) && <Spinner size={"tiny"}/>}
                            {canBeDeleted ? "Yes, delete" : "Yes, deactivate"}
                        </Button>
                        <DialogTrigger disableButtonEnhancement>
                            <Button
                                id={"deactivate-dialog-cancel-btn"}
                                appearance="secondary"
                                shape={"circular"}
                                disabled={deactivating || deleting}
                                onClick={onDismiss}>
                                No, Cancel
                            </Button>
                        </DialogTrigger>
                    </DialogActions>
                </DialogBody>
            </DialogSurface>
        </Dialog>}
    </>;
};

export default AppUserDeactivateDialog;