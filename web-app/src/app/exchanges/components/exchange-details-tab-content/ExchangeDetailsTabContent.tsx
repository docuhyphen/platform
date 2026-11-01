import React from 'react';
import ExchangeDocumentSidebar from '../exchange-document-sidebar/ExchangeDocumentSidebar.tsx';
import ExchangeDocumentsSection from '../exchange-documents-section/ExchangeDocumentsSection.tsx';
import ExchangeFieldsTab from '../exchange-fields-tab/ExchangeFieldsTab.tsx';
import ExchangeWorkflowTab from '../exchange-workflow-tab/ExchangeWorkflowTab.tsx';
import ExchangeAuditTab from '../exchange-audit-tab/ExchangeAuditTab.tsx';
import ExchangeInformationRequestsTab
    from '../../../information-requests/exchange-tab/exchange-information-requests-tab/ExchangeInformationRequestsTab.tsx';
import {ExchangesWorkspace} from '../../useExchangesWorkspace.ts';
import {useExchangeDetailsTabContentStyles} from './ExchangeDetailsTabContentStyles.tsx';

interface ExchangeDetailsTabContentProps
{
    workspace: ExchangesWorkspace;
}

const ExchangeDetailsTabContent: React.FC<ExchangeDetailsTabContentProps> = ({workspace}) =>
{
    const styles = useExchangeDetailsTabContentStyles();
    const {
        detailsActiveTab,
        exchangeDetails,
        canViewBusinessFields,
        canViewWorkflows,
        canViewExchangeAudit,
        informationRequests,
        selectedSidebarExchangeDocument,
        isDocumentSidebarOpen,
        setIsDocumentSidebarOpen,
        previewerLink,
    } = workspace;
    const scrollableSectionClassName = `${styles.documentsSection} ${styles.scrollableTabContent}`;

    return (
        <>
            {detailsActiveTab === 'documents' && (
                <div className={styles.documentsSectionContainer}
                     id={"documentsSectionContainer"}>
                    <div className={styles.documentsSection}
                         id={"documentsSection"}>
                        <ExchangeDocumentsSection workspace={workspace}/>
                    </div>
                    {selectedSidebarExchangeDocument && isDocumentSidebarOpen &&

                        <ExchangeDocumentSidebar
                            isOpen={isDocumentSidebarOpen}
                            onOpen={setIsDocumentSidebarOpen}
                            exchange={exchangeDetails}
                            exchangeDocument={selectedSidebarExchangeDocument}
                            pageNumber={previewerLink.sidebarLinkablePageNumber}
                            onNavigateToPage={previewerLink.canNavigatePreviewerToPage
                                ? previewerLink.handleNavigatePreviewerToPage
                                : undefined}/>
                    }
                </div>
            )}

            {detailsActiveTab === 'details' && canViewBusinessFields && (
                <div id={"exchanges-documents-section-container"}
                     className={styles.documentsSectionContainer}>
                    <div
                        id={"exchanges-documents-section"}
                        className={scrollableSectionClassName}>
                        <ExchangeFieldsTab exchange={exchangeDetails}/>
                    </div>
                </div>
            )}

            {detailsActiveTab === 'information-requests' && informationRequests.visible && (
                <div id={"exchange-information-requests-tab-container"}
                     className={styles.documentsSectionContainer}>
                    <div id={"exchange-information-requests-tab-content"}
                         className={scrollableSectionClassName}>
                        <ExchangeInformationRequestsTab exchangeId={exchangeDetails.id}
                                                        state={informationRequests}/>
                    </div>
                </div>
            )}

            {detailsActiveTab === 'workflow' && canViewWorkflows && (
                <div id={"exchanges-documents-section-container-2"}
                     className={styles.documentsSectionContainer}>
                    <div
                        id={"exchanges-documents-section-2"}
                        className={scrollableSectionClassName}>
                        <ExchangeWorkflowTab exchange={exchangeDetails}/>
                    </div>
                </div>
            )}

            {detailsActiveTab === 'audit' && canViewExchangeAudit && (
                <div id={"exchanges-documents-section-container-3"}
                     className={styles.documentsSectionContainer}>
                    <div
                        id={"exchanges-documents-section-3"}
                        className={scrollableSectionClassName}>
                        <ExchangeAuditTab exchange={exchangeDetails}/>
                    </div>
                </div>
            )}
        </>
    );
};

export default ExchangeDetailsTabContent;
