import React from 'react';
import ExchangeDetailsHeader from '../exchange-details-header/ExchangeDetailsHeader.tsx';
import ExchangeTabsHeader from '../exchange-tabs-header/ExchangeTabsHeader.tsx';
import {ExchangesWorkspace} from '../../useExchangesWorkspace.ts';

interface ExchangeDetailsPaneHeadersProps
{
    workspace: ExchangesWorkspace;
}

const ExchangeDetailsPaneHeaders: React.FC<ExchangeDetailsPaneHeadersProps> = ({workspace}) =>
{
    const {
        isMobile,
        exchangeDetails,
        dialogs,
        permissions,
        onRecreateRejectedExchange,
        changeSelectedExchangeId,
        detailsActiveTab,
        setDetailsActiveTab,
        canViewExchangeAudit,
        canViewBusinessFields,
        canViewWorkflows,
        informationRequests,
        isDocumentToolbarVisible,
        setIsDocumentToolbarVisible,
    } = workspace;

    return (
        <>
            <ExchangeDetailsHeader
                exchangeDetails={exchangeDetails}
                setIsDocumentAddDialogOpen={dialogs.setIsDocumentAddDialogOpen}
                setIsExchangeEndDialogOpen={dialogs.setIsExchangeEndDialogOpen}
                setIsExchangeRescindDialogOpen={dialogs.setIsExchangeRescindDialogOpen}
                setIsDeletedExchangeDialogOpen={dialogs.setIsDeletedExchangeDialogOpen}
                setIsExchangeEditDialogOpen={dialogs.setIsExchangeEditDialogOpen}
                setIsExchangeDetailedViewDialogOpen={dialogs.setIsExchangeDetailedViewDialogOpen}
                setIsExchangeAccessManagementDialogOpen={dialogs.setIsExchangeAccessManagementDialogOpen}
                exchangePermissions={permissions}
                onRecreateRejectedExchange={onRecreateRejectedExchange}
                onBackToList={isMobile ? () => changeSelectedExchangeId(null) : undefined}
            />

            <ExchangeTabsHeader activeTab={detailsActiveTab}
                                documents={exchangeDetails?.documents || []}
                                canDownloadZip={!!permissions?.canDownloadDocumentsZip}
                                canViewAudit={canViewExchangeAudit}
                                canViewDetails={canViewBusinessFields}
                                canViewWorkflow={canViewWorkflows}
                                canViewInformationRequests={informationRequests.visible}
                                isDocumentToolbarVisible={isDocumentToolbarVisible}
                                onTabChange={setDetailsActiveTab}
                                onDownloadZip={() => dialogs.setIsDocumentZipDialogOpen(true)}
                                onToggleDocumentToolbar={() => setIsDocumentToolbarVisible(visible => !visible)}/>
        </>
    );
};

export default ExchangeDetailsPaneHeaders;
