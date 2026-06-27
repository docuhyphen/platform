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
import {LinkStatus, OrganizationExchangeLinkBasicDto, ResponseError} from "../../../../models/models.tsx";
import {DismissRegular} from "@fluentui/react-icons";
import {useParingRequestRejectDialogStyles} from "./ParingRequestRejectDialogStyles.tsx";
import {acceptOrRejectOrganizationLink} from "../../../../../services/organizationExchange.ts";

interface ParingRequestDeleteDialogProps
{
    orgPair: OrganizationExchangeLinkBasicDto | null,
    isOpen: boolean;
    onDismiss: () => void;
    onRejected: (orgPair: OrganizationExchangeLinkBasicDto) => void;
}

const ParingRequestRejectDialog: React.FC<ParingRequestDeleteDialogProps> = (
    {
        orgPair,
        isOpen,
        onDismiss,
        onRejected
    }) =>
{
    const styles = useParingRequestRejectDialogStyles()
    const token = useToken();
    const [deletingOrgPairRequest, setAcceptingOrgPairRequest] = React.useState(false);
    const globalStyles = useGlobalStyles()
    const [dialogErrorMessage, setDialogErrorMessage] = useState<string | null>()

    useEffect(() =>
    {
        if (isOpen)
        {
            setDialogErrorMessage(null)
        }
    }, [isOpen]);

    const onReject = async () =>
    {
        setDialogErrorMessage(null)
        setAcceptingOrgPairRequest(true)

        try
        {
            await acceptOrRejectOrganizationLink(orgPair.id, LinkStatus.REJECTED, token!)
            onRejected(orgPair!);
        }
        catch (error: ResponseError | any)
        {
            const errorMessage = ((error as ResponseError)?.errorMessage) || "An unknown error occurred attempting to accept";

            setDialogErrorMessage(errorMessage)
        }
        finally
        {
            setAcceptingOrgPairRequest(false);
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
                            id={"button-dismiss-reject-dialog-error"}
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
        <Dialog modalType="alert" open={isOpen}>
            <DialogSurface>
                <DialogBody>
                    <DialogTitle>Rejecting Paring Request</DialogTitle>
                    <DialogContent className={styles.dialogContent}>
                        {renderDialogError()}
                        <div>
                            Are you sure you want to reject this request from
                            <Text weight={"semibold"}> {orgPair && orgPair.requestingOrganizationName}? </Text>
                            <p>
                                <Text weight={"semibold"}> What does this mean? </Text>
                                When other organizations want to share documents with you, they won't be able to
                                search and select users/groups within your organization.
                            </p>
                        </div>
                    </DialogContent>
                    <DialogActions>
                        <Button
                            id={"button-confirm-reject-request"}
                            appearance="primary"
                            className={globalStyles.buttonWithLoading}
                            shape={"circular"}
                            onClick={onReject}>
                            {deletingOrgPairRequest && <Spinner size={"tiny"}/>}
                            Yes, Reject
                        </Button>
                        <DialogTrigger disableButtonEnhancement>
                            <Button
                                id={"button-cancel-reject-request"}
                                appearance="secondary"
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

export default ParingRequestRejectDialog;