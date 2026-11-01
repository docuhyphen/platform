import {
    Button,
    Dialog,
    DialogActions,
    DialogBody,
    DialogContent,
    DialogSurface,
    DialogTitle,
    MessageBar,
    MessageBarBody,
    Spinner,
    Tooltip,
} from "@fluentui/react-components";
import React from "react";
import {useGlobalStyles} from "../../../../GlobalStyles.tsx";
import {useHelpSidebar} from "../../../../context/HelpSidebarContext.tsx";
import {ExchangeDetailedDto} from "../../../models/models.tsx";
import {InfoIcon} from "../../../components/IconBundles.tsx";
import ExchangeAccessManagementContent from "./ExchangeAccessManagementContent.tsx";
import {useAccessManagementDialogStyles} from "./ExchangeAccessManagementDialogStyles.tsx";
import {useExchangeAccessManagementDialog} from "./useExchangeAccessManagementDialog.ts";

interface ExchangeAccessManagementDialogProps
{
    isOpen: boolean;
    onDismiss: () => void;
    exchange: ExchangeDetailedDto | null;
    onExchangeAccessManagementUpdated: (exchange: ExchangeDetailedDto) => void;
}

const EmptyAccessManagementDialog = (
    {isOpen, onDismiss}: Pick<ExchangeAccessManagementDialogProps, "isOpen" | "onDismiss">,
) => (
    <Dialog
        modalType={"alert"}
        open={isOpen}
    >
        <DialogSurface id={"exchange-access-management-dialog-dialog-surface-1"}>
            <DialogBody id={"exchange-access-management-dialog-dialog-body-1"}>
                <DialogTitle id={"exchange-access-management-dialog-dialog-title-1"}>Manage access</DialogTitle>
                <DialogContent id={"exchange-access-management-dialog-dialog-content-1"}>
                    <MessageBar id={"exchange-access-management-dialog-message-bar-1"}
                                intent={"warning"}>
                        <MessageBarBody id={"exchange-access-management-dialog-message-bar-body-1"}>No Exchange is selected yet.</MessageBarBody>
                    </MessageBar>
                </DialogContent>
                <DialogActions id={"exchange-access-management-dialog-dialog-actions-1"}>
                    <Button
                        id={"access-mgmt-empty-close-btn"}
                        appearance={"secondary"}
                        shape={"circular"}
                        onClick={onDismiss}
                    >
                        Close
                    </Button>
                </DialogActions>
            </DialogBody>
        </DialogSurface>
    </Dialog>
);

const ExchangeAccessManagementDialog: React.FC<ExchangeAccessManagementDialogProps> = props =>
{
    const styles = useAccessManagementDialogStyles();
    const globalStyles = useGlobalStyles();
    const {openHelpArticle} = useHelpSidebar();
    const state = useExchangeAccessManagementDialog(props);
    if (!props.exchange)
    {
        return <EmptyAccessManagementDialog isOpen={props.isOpen}
                                            onDismiss={props.onDismiss}/>;
    }
    const exchange = props.exchange;
    return (
        <Dialog
            modalType={"alert"}
            open={props.isOpen}
        >
            <DialogSurface id={"exchange-access-management-dialog-dialog-surface-2"}>
                <DialogBody id={"exchange-access-management-dialog-dialog-body-2"}>
                    <DialogTitle id={"exchange-access-management-dialog-dialog-title-2"}>
                        <div id={"exchange-access-management-dialog-div-1"}
                             className={styles.titleRow}>
                            <span id={"exchange-access-management-dialog-span-1"}>Manage access</span>
                            <Tooltip
                                content={"Learn how access works"}
                                relationship={"label"}
                            >
                                <Button
                                    id={"access-mgmt-help-btn"}
                                    icon={<InfoIcon/>}
                                    appearance={"subtle"}
                                    shape={"circular"}
                                    size={"medium"}
                                    onClick={() => openHelpArticle("manage-access")}
                                    aria-label={"How access works"}
                                />
                            </Tooltip>
                        </div>
                    </DialogTitle>
                    <DialogContent id={"exchange-access-management-dialog-dialog-content-2"}>
                        <ExchangeAccessManagementContent
                            exchange={exchange}
                            state={state}
                        />
                    </DialogContent>
                    <DialogActions id={"exchange-access-management-dialog-dialog-actions-2"}>
                        <Button
                            id={"access-mgmt-save-btn"}
                            appearance={"primary"}
                            className={globalStyles.buttonWithLoading}
                            shape={"circular"}
                            onClick={state.onUpdate}
                        >
                            {state.updatingExchange && <Spinner id={"exchange-access-management-dialog-spinner-1"}
                                                                size={"tiny"}/>}
                            Save changes
                        </Button>
                        <Button
                            id={"access-mgmt-cancel-btn"}
                            appearance={"secondary"}
                            shape={"circular"}
                            disabled={state.updatingExchange}
                            onClick={props.onDismiss}
                        >
                            Cancel
                        </Button>
                    </DialogActions>
                </DialogBody>
            </DialogSurface>
        </Dialog>
    );
};

export default ExchangeAccessManagementDialog;
