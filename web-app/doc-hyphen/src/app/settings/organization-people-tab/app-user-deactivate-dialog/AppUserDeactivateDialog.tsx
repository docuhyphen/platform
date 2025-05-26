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
import {
    deactivateOrganizationUser,
    deleteOrganizationGroup,
    OrganizationGroupBasicDto
} from "../../../../services/organizationApi.ts";
import {AppUserDetailedDto} from "../../../models/models.tsx";

interface AppUserDeactivateDialogProps
{
    isOpen: boolean;
    onDismiss: () => void;
    appUser: AppUserDetailedDto
    organizationId: string
    onDeactivated: (appUserId: string) => void;
}

const AppUserDeactivateDialog: React.FC<AppUserDeactivateDialogProps> = (
    {
        isOpen,
        onDismiss,
        appUser,
        organizationId,
        onDeactivated
    }) =>
{
    const token = useToken();
    const [deactivating, setDeactivating] = React.useState(false);
    const globalStyles = useGlobalStyles()

    const onDeactivate = async () =>
    {
        setDeactivating(true)

        try
        {
            await deactivateOrganizationUser(organizationId, appUser.id!, token!);
            onDeactivated(appUser.id!)
        }
        catch (err: any)
        {
            // setError(err.message || "Failed to deactivate user");
            console.error("Failed to deactivate user:", err);
        }
        finally
        {
            setDeactivating(false);
        }
    }

    return <>
        {<Dialog modalType="alert" open={isOpen}>
            <DialogSurface>
                <DialogBody>
                    <DialogTitle>
                        Deactivating {appUser && `${appUser.person.firstName} ${appUser.person.lastName}`}
                    </DialogTitle>
                    <DialogContent>
                        Are you sure you want to deactivate?
                    </DialogContent>
                    <DialogActions>
                        <Button appearance="primary"
                                className={globalStyles.buttonWithLoading}
                                shape={"circular"}
                                onClick={onDeactivate}>
                            {deactivating && <Spinner size={"tiny"}/>}
                            Yes, Delete
                        </Button>
                        <DialogTrigger disableButtonEnhancement>
                            <Button appearance="secondary"
                                    shape={"circular"}
                                    disabled={deactivating}
                                    onClick={onDismiss}>
                                No, Cancel
                            </Button>
                        </DialogTrigger>
                    </DialogActions>
                </DialogBody>
            </DialogSurface>
        </Dialog>
        }
    </>
}

export default AppUserDeactivateDialog;