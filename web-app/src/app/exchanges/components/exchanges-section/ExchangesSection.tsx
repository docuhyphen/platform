import React from 'react';
import {mergeClasses} from '@fluentui/react-components';
import ExchangeList from '../exchange-list/ExchangeList.tsx';
import ExchangeDialogsGroup from '../exchange-dialog-group/ExchangeDialogsGroup.tsx';
import ExchangeDetailsColumn from '../exchange-details-column/ExchangeDetailsColumn.tsx';
import ExchangeDetailsFallbacks from '../exchange-details-fallbacks/ExchangeDetailsFallbacks.tsx';
import {resolveExchangeDetailsFallbacks} from '../exchange-details-fallbacks/exchangeDetailsFallbacks.ts';
import {ExchangesWorkspace} from '../../useExchangesWorkspace.ts';
import {useExchangesSectionStyles} from './ExchangesSectionStyles.tsx';

interface ExchangesSectionProps
{
    workspace: ExchangesWorkspace;
}

const ExchangesSection: React.FC<ExchangesSectionProps> = ({workspace}) =>
{
    const styles = useExchangesSectionStyles();
    const {
        isMobile,
        selectedExchangeId,
        paneNavigationDirection,
        activeListTab,
        setActiveListTab,
        inboxRole,
        setInboxRole,
        setExchangeList,
        setTabCounts,
        setIsExchangeListLoading,
        changeSelectedExchangeId,
        tabCounts,
        dialogs,
    } = workspace;
    const hasMobileSelection = !!selectedExchangeId;
    const listPaneClassName = mergeClasses(
        styles.listPaneWrapper,
        isMobile && hasMobileSelection ? styles.listPaneHidden : undefined,
        isMobile && !hasMobileSelection && paneNavigationDirection === "back"
            ? styles.listPaneSlideInFromLeft
            : undefined,
    );
    const showDetailsColumnFallbacks = !isMobile;
    const fallbacks = resolveExchangeDetailsFallbacks(workspace);

    return (
        <section id={"exchanges-container"}
                 className={styles.container}>

            <div id={"exchanges-container-div"}
                 className={listPaneClassName}>
                <ExchangeList
                    onSelectionChange={changeSelectedExchangeId}
                    onExchangeListChange={setExchangeList}
                    onTabChange={setActiveListTab}
                    onInboxRoleChange={setInboxRole}
                    onTabCountsChange={setTabCounts}
                    onLoadingChange={setIsExchangeListLoading}
                    controlledSelectedId={selectedExchangeId}
                    controlledActiveTab={activeListTab}
                    controlledInboxRole={inboxRole}
                />
            </div>

            <ExchangeDetailsColumn workspace={workspace}
                                   showDetailsColumnFallbacks={showDetailsColumnFallbacks}/>

            {showDetailsColumnFallbacks &&
                <ExchangeDetailsFallbacks fallbacks={fallbacks}
                                          tabCounts={tabCounts}
                                          onOpenTab={setActiveListTab}/>
            }

            <ExchangeDialogsGroup
                dialogs={dialogs}
                exchangeDetails={workspace.exchangeDetails}
                selectedExchangeId={selectedExchangeId}
                selectedUploadExchangeDocument={workspace.selectedUploadExchangeDocument}
                selectedUpdateExchangeDocument={workspace.selectedUpdateExchangeDocument}
                setSelectedUpdateExchangeDocument={workspace.setSelectedUpdateExchangeDocument}
                onNewDocumentAdded={workspace.onNewDocumentAdded}
                onDocumentUploaded={workspace.onDocumentUploaded}
                onDocumentUpdated={workspace.onDocumentUpdated}
                onExchangeDeleted={workspace.onExchangeDeleted}
                onExchangeEnded={workspace.onExchangeEnded}
                onExchangeRescinded={workspace.onExchangeRescinded}
                onExchangeEdited={workspace.onExchangeEdited}
                onExchangeAccessManagementUpdated={workspace.onExchangeAccessManagementUpdated}
            />

        </section>
    );
};

export default ExchangesSection;
