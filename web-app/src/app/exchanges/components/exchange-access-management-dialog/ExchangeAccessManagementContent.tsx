import {MessageBar, MessageBarBody, Tab, TabList, Text} from "@fluentui/react-components";
import {useGlobalStyles} from "../../../../GlobalStyles.tsx";
import {ExchangeDetailedDto} from "../../../models/models.tsx";
import AccessPermissionsPanel from "./access-permissions-panel/AccessPermissionsPanel.tsx";
import AddPersonPanel from "./add-person-panel/AddPersonPanel.tsx";
import {accessManagementTabIds} from "./exchangeAccessManagementTypes.ts";
import {useAccessManagementDialogStyles} from "./ExchangeAccessManagementDialogStyles.tsx";
import ExchangeSettingsPanel from "./exchange-settings-panel/ExchangeSettingsPanel.tsx";
import PeopleSummaryPanel from "./people-summary-panel/PeopleSummaryPanel.tsx";
import ReplacePrimaryRecipientPanel from "./replace-primary-recipient-panel/ReplacePrimaryRecipientPanel.tsx";
import {ExchangeAccessManagementDialogState} from "./useExchangeAccessManagementDialog.ts";

interface ExchangeAccessManagementContentProps
{
    exchange: ExchangeDetailedDto;
    state: ExchangeAccessManagementDialogState;
}

const ExchangeAccessManagementContent = ({exchange, state}: ExchangeAccessManagementContentProps) =>
{
    const styles = useAccessManagementDialogStyles();
    const globalStyles = useGlobalStyles();
    return (
        <>
            {state.dialogErrorMessage && (
                <MessageBar id={"exchange-access-management-content-message-bar-1"}
                            intent={"error"}>
                    <MessageBarBody id={"exchange-access-management-content-message-bar-body-1"}>
                        <Text id={"exchange-access-management-content-text-1"}
                              size={200}>{state.dialogErrorMessage}</Text>
                    </MessageBarBody>
                </MessageBar>
            )}
            <TabList
                id={"exchange-access-management-content-tab-list-1"}
                selectedValue={state.selectedTab}
                onTabSelect={state.onTabSelect}
                className={styles.tabList}
            >
                <Tab id={"exchange-access-management-content-tab-1"}
                     value={accessManagementTabIds.people}>Summary</Tab>
                <Tab id={"exchange-access-management-content-tab-2"}
                     value={accessManagementTabIds.access}>Access &amp; permissions</Tab>
                <Tab id={"exchange-access-management-content-tab-3"}
                     value={accessManagementTabIds.settings}>Exchange settings</Tab>
            </TabList>
            <div id={"exchange-access-management-content-div-1"}
                 className={styles.tabPanel}>
                {state.accessView === "replace-primary" && (
                    <ReplacePrimaryRecipientPanel
                        exchangeId={exchange.id}
                        onBack={() => state.setAccessView("list")}
                        onReplaced={state.onPrimaryRecipientReplaced}
                    />
                )}
                {state.accessView !== "replace-primary"
                    && state.selectedTab === accessManagementTabIds.people && (
                    <PeopleSummaryPanel
                        exchange={exchange}
                        onReplacePrimary={() => state.setAccessView("replace-primary")}
                    />
                )}
                {state.selectedTab === accessManagementTabIds.access && state.accessView === "add-person" && (
                    <AddPersonPanel
                        exchangeId={exchange.id}
                        onBack={() => state.setAccessView("list")}
                        onPersonAdded={() => state.setAccessView("list")}
                    />
                )}
                {state.selectedTab === accessManagementTabIds.access && state.accessView === "list" && (
                    <AccessPermissionsPanel
                        exchangeId={exchange.id}
                        allowDocumentAddition={state.allowDocumentAddition}
                        setAllowDocumentAddition={state.setAllowDocumentAddition}
                        allowDocumentDeletion={state.allowDocumentDeletion}
                        setAllowDocumentDeletion={state.setAllowDocumentDeletion}
                        allowDocumentDownload={state.allowDocumentDownload}
                        setAllowDocumentDownload={state.setAllowDocumentDownload}
                        allowDocumentUpdate={state.allowDocumentUpdate}
                        setAllowDocumentUpdate={state.setAllowDocumentUpdate}
                        allowDocumentUpload={state.allowDocumentUpload}
                        setAllowDocumentUpload={state.setAllowDocumentUpload}
                        allowedDownloadFormats={state.allowedDownloadFormats}
                        setAllowedDownloadFormats={state.setAllowedDownloadFormats}
                        onAddPerson={() => state.setAccessView("add-person")}
                    />
                )}
                {state.accessView !== "replace-primary"
                    && state.selectedTab === accessManagementTabIds.settings && (
                    <ExchangeSettingsPanel
                        requireRecipientSignIn={state.requireRecipientSignIn}
                        setRequireRecipientSignIn={state.setRequireRecipientSignIn}
                        sendingAccessCode={state.sendingAccessCode}
                        sendingInvitation={state.sendingInvitation}
                        resendCooldownRemaining={state.resendCooldownRemaining}
                        onSendAccessCode={state.onSendAccessCode}
                        onResendInvitation={state.onResendInvitation}
                        buttonWithLoadingClassName={globalStyles.buttonWithLoading}
                        noAuthAccessValidityDays={state.noAuthAccessValidityDays}
                        setNoAuthAccessValidityDays={state.setNoAuthAccessValidityDays}
                        accessCodeStatus={state.accessCodeStatus}
                        accessCodeError={state.accessCodeError}
                        invitationStatus={state.invitationStatus}
                        invitationError={state.invitationError}
                    />
                )}
            </div>
        </>
    );
};

export default ExchangeAccessManagementContent;
