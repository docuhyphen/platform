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
import {useParingRequestRejectDialogStyles} from "./ParingRequestAcceptDialogStyles.tsx";
import {
    acceptOrRejectOrganizationLink
} from "../../../../../services/organizationExchange.ts";

interface ParingRequestDeleteDialogProps
{
    orgPair: OrganizationExchangeLinkBasicDto | null,
    isOpen: boolean;
    onDismiss: () => void;
    onAccepted: (orgPair: OrganizationExchangeLinkBasicDto | null) => void;
}

const ParingRequestAcceptDialog: React.FC<ParingRequestDeleteDialogProps> = (
    {
        orgPair,
        isOpen,
        onDismiss,
        onAccepted
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
    }, [isOpen])

    const onAccept = async () =>
    {
        setAcceptingOrgPairRequest(true)
        setDialogErrorMessage(null)

        try
        {
            await acceptOrRejectOrganizationLink(orgPair.id, LinkStatus.ACCEPTED, token)
            onAccepted(orgPair);
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
                            id={"button-dismiss-accept-dialog-error"}
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
                    <DialogTitle>Accepting Paring Request</DialogTitle>
                    <DialogContent className={styles.dialogContent}>
                        {renderDialogError()}
                        <div>
                            Are you sure you want to accept this request from
                            <Text weight={"semibold"}> {orgPair && orgPair.requestingOrganizationName}? </Text>
                            <p>
                                <Text weight={"semibold"}> What does this mean? </Text>
                                When other organizations want to share documents with you, they'll be able to
                                search and select users/groups within your organization.
                            </p>
                        </div>
                    </DialogContent>
                    <DialogActions>
                        <Button
                            id={"button-confirm-accept-request"}
                            appearance="primary"
                            className={globalStyles.buttonWithLoading}
                            shape={"circular"}
                            onClick={onAccept}>
                            {deletingOrgPairRequest && <Spinner size={"tiny"}/>}
                            Yes, Accept
                        </Button>
                        <DialogTrigger disableButtonEnhancement>
                            <Button
                                id={"button-cancel-accept-request"}
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

export default ParingRequestAcceptDialog;