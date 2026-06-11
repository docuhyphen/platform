import React, {useEffect, useState} from "react";
import useToken from "../../../../../context/useToken.tsx";
import {useGlobalStyles} from "../../../../../GlobalStyles.tsx";
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
import {OrganizationExchangeLinkBasicDto, ResponseError} from "../../../../models/models.tsx";
import {DismissRegular} from "@fluentui/react-icons";
import {useParingRequestDeleteDialogStyles} from "./ParingRequestDeleteDialogStyles.tsx";
import {deleteOrganizationLink} from "../../../../../services/organizationExchange.ts";

interface ParingRequestDeleteDialogProps
{
    orgPair: OrganizationExchangeLinkBasicDto | null,
    isOpen: boolean;
    onDismiss: () => void;
    onDeleted: (orgPair: OrganizationExchangeLinkBasicDto | null) => void;
}

const ParingRequestDeleteDialog: React.FC<ParingRequestDeleteDialogProps> = (
    {
        orgPair,
        isOpen,
        onDismiss,
        onDeleted
    }) =>
{
    const styles = useParingRequestDeleteDialogStyles()
    const token = useToken();
    const [deletingOrgPairRequest, setDeletingOrgPairRequest] = React.useState(false);
    const globalStyles = useGlobalStyles()
    const [dialogErrorMessage, setDialogErrorMessage] = useState<string | null>()

    useEffect(() =>
    {
        if (isOpen)
        {
            setDialogErrorMessage(null)
        }
    }, [isOpen])

    const onDelete = async () =>
    {
        setDeletingOrgPairRequest(true)
        setDialogErrorMessage('')

        try
        {
            await deleteOrganizationLink(orgPair.id, token)
            onDeleted(orgPair);
        }
        catch (error: ResponseError | any)
        {
            const errorMessage = ((error as ResponseError)?.errorMessage) || "An unknown error occurred attempting to delete";
            setDialogErrorMessage(errorMessage)
        }
        finally
        {
            setDeletingOrgPairRequest(false);
            setDialogErrorMessage('')
        }
    }
    const renderDialogError = () => (
        dialogErrorMessage && (
            <MessageBar intent={"error"}>
                <MessageBarBody>
                    <Text size={200}> {dialogErrorMessage} </Text>
                </MessageBarBody>
                <MessageBarActions
                    containerAction={
                        <Button
                            onClick={() => setDialogErrorMessage(null)}
                            appearance="transparent"
                            icon={<DismissRegular/>}
                        />
                    }
                />
            </MessageBar>
        )
    );

    return <>
        <Dialog modalType="alert" open={isOpen}>
            <DialogSurface>
                <DialogBody>
                    <DialogTitle>Deleting Paring Request</DialogTitle>
                    <DialogContent className={styles.dialogContent}>
                        {renderDialogError()}
                        <div>
                            Are you sure you want to delete this request to
                            <Text weight={"bold"}> {orgPair && orgPair.requestedOrganizationName}? </Text>
                        </div>
                    </DialogContent>
                    <DialogActions>
                        <Button appearance="primary"
                                className={globalStyles.buttonWithLoading}
                                shape={"circular"}
                                onClick={onDelete}>
                            {deletingOrgPairRequest && <Spinner size={"tiny"}/>}
                            Yes, Delete
                        </Button>
                        <DialogTrigger disableButtonEnhancement>
                            <Button appearance="secondary"
                                    shape={"circular"}
                                    disabled={deletingOrgPairRequest}
                                    onClick={() =>
                                    {
                                        setDialogErrorMessage("");
                                        onDismiss()
                                    }}>
                                No, Cancel
                            </Button>
                        </DialogTrigger>
                    </DialogActions>
                </DialogBody>
            </DialogSurface>
        </Dialog>
    </>
}

export default ParingRequestDeleteDialog;