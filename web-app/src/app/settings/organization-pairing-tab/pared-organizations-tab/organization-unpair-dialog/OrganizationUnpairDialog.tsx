import React, {useState} from "react";
import {
    Button,
    Dialog,
    DialogActions,
    DialogBody,
    DialogContent,
    DialogSurface,
    DialogTitle,
    DialogTrigger,
    MessageBar,
    MessageBarActions,
    MessageBarBody,
    Spinner,
    Text
} from "@fluentui/react-components";
import {DismissRegular} from "@fluentui/react-icons";
import {OrganizationExchangeLinkBasicDto, ResponseError} from "../../../../models/models";
import {deleteOrganizationLink} from "../../../../../services/organizationExchange";

interface UnpairOrganizationDialogProps
{
    isOpen: boolean;
    orgPair: OrganizationExchangeLinkBasicDto | null;
    onDismiss: () => void;
    onUnpaired: (orgPair: OrganizationExchangeLinkBasicDto) => void;
    setError: (msg: string | null) => void;
    token: string | null;
}

const OrganizationUnpairDialog: React.FC<UnpairOrganizationDialogProps> = (
    {
        isOpen,
        orgPair,
        onDismiss,
        onUnpaired,
        setError,
        token
    }) =>
{
    const [loading, setLoading] = useState(false);
    const [dialogErrorMessage, setDialogErrorMessage] = useState<string | null>(null);

    const onConfirmUnpair = async () =>
    {
        setDialogErrorMessage(null);
        setLoading(true);
        try
        {
            await deleteOrganizationLink(orgPair!.id, token);
            onUnpaired(orgPair!);
        }
        catch (error: unknown)
        {
            const errorMessage = ((error as ResponseError)?.errorMessage) || "An unknown error occurred attempting to unpair";
            setDialogErrorMessage(errorMessage);
            setError(errorMessage);
        }
        finally
        {
            setLoading(false);
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
                            id={"button-dismiss-unpair-dialog-error"}
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

    return (
        <Dialog modalType="alert" open={isOpen}>
            <DialogSurface>
                <DialogBody>
                    <DialogTitle>Unpair Organization</DialogTitle>
                    <DialogContent>
                        {renderDialogError()}
                        <div>
                            Are you sure you want to unpair from
                            <Text weight={"semibold"}> {orgPair && orgPair.requestingOrganizationName}? </Text>
                            <p>
                                <Text weight={"semibold"}> What does this mean? </Text>
                                This organization will no longer be able to search and select users/groups from
                                your organization to share documents with you.
                            </p>
                        </div>
                    </DialogContent>
                    <DialogActions>
                        <Button
                            id={"button-confirm-unpair"}
                            appearance="primary"
                            shape={"circular"}
                            onClick={onConfirmUnpair}
                            disabled={loading}>
                            {loading && <Spinner size={"tiny"}/>}
                            Yes, Unpair
                        </Button>
                        <DialogTrigger disableButtonEnhancement>
                            <Button
                                id={"button-cancel-unpair"}
                                appearance="secondary"
                                shape={"circular"}
                                disabled={loading}
                                onClick={onDismiss}>
                                No, Cancel
                            </Button>
                        </DialogTrigger>
                    </DialogActions>
                </DialogBody>
            </DialogSurface>
        </Dialog>
    );
};

export default OrganizationUnpairDialog;