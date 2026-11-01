import {useState} from 'react';
import {CommunicationSummaryDto, CommunicationScope} from '../../models/models';
import {Capability} from '../../models/models';
import CommunicationEditorDialog from './CommunicationEditorDialog';
import {useAuth} from '../../../context/AuthContext';
import {useCommunicationsTabStyles} from './CommunicationsTabStyles';
import CommunicationsPagination from './communications-pagination/CommunicationsPagination';
import CommunicationsToolbar from './communications-toolbar/CommunicationsToolbar';
import CommunicationsScopeTabs from './communications-scope-tabs/CommunicationsScopeTabs';
import CommunicationsListContent from './communications-list-content/CommunicationsListContent';
import {CommunicationListItemContext, CommunicationListTab} from './communicationListTypes';
import {COMMUNICATIONS_PAGE_SIZE} from './communicationListFilters';
import {createCommunicationListActions} from './communicationListActions';
import {useCommunicationsLoader} from './useCommunicationsLoader';
import {useCommunicationListFilters} from './useCommunicationListFilters';
import {useCommunicationsViewMode} from './useCommunicationsViewMode';

const CommunicationsTab = () =>
{
    const styles = useCommunicationsTabStyles();
    const {appUserPersonOrganization, hasCapability} = useAuth();

    const hasOrg = !!appUserPersonOrganization?.isActive;
    const canManageOrganization =
        appUserPersonOrganization?.isActive &&
        hasCapability(Capability.ORG_POLICY_MANAGE);
    const {viewMode, handleViewModeChange} = useCommunicationsViewMode();

    const [activeTab, setActiveTab] = useState<CommunicationListTab>('PERSONAL');
    const [editorOpen, setEditorOpen] = useState(false);
    const [editingCommunication, setEditingCommunication] = useState<CommunicationSummaryDto | undefined>();
    const {communications, loading, error, loadCommunications, clearCommunications} = useCommunicationsLoader(activeTab);
    const filters = useCommunicationListFilters(communications);

    const openEdit = (communication: CommunicationSummaryDto) =>
    {
        setEditingCommunication(communication);
        setEditorOpen(true);
    };

    const openCreate = () =>
    {
        setEditingCommunication(undefined);
        setEditorOpen(true);
    };

    const changeTab = (tab: CommunicationListTab) =>
    {
        setActiveTab(tab);
        clearCommunications();
        filters.resetFilters();
    };

    const canCreate =
        activeTab === 'PERSONAL' ||
        (activeTab === 'ORG' && !!canManageOrganization);

    const itemContext: CommunicationListItemContext = {
        showPublication: activeTab !== 'PERSONAL',
        canManageItem: communication =>
            communication.scope === 'PERSONAL' ||
            (communication.scope === 'ORG' && !!canManageOrganization),
        actions: createCommunicationListActions(loadCommunications, openEdit),
    };

    return (
        <>
            <div id={"communications-tab-div-1"}
                 className={styles.outerContainer}>
                <CommunicationsScopeTabs
                    activeTab={activeTab}
                    hasOrg={hasOrg}
                    canCreate={canCreate}
                    onTabChange={changeTab}
                    onCreate={openCreate}
                />

                <CommunicationsToolbar
                    searchQuery={filters.searchQuery}
                    onSearchChange={filters.changeSearch}
                    availableTags={filters.availableTags}
                    selectedTags={filters.selectedTags}
                    onTagToggle={filters.toggleTag}
                    statusFilter={filters.statusFilter}
                    onStatusFilterChange={filters.changeStatusFilter}
                    publicationFilter={filters.publicationFilter}
                    onPublicationFilterChange={filters.changePublicationFilter}
                    showPublicationFilter={activeTab !== 'PERSONAL'}
                    sortOrder={filters.sortOrder}
                    onSortOrderChange={filters.changeSortOrder}
                    viewMode={viewMode}
                    onViewModeChange={handleViewModeChange}
                />

                <CommunicationsListContent
                    activeTab={activeTab}
                    loading={loading}
                    error={error}
                    totalCount={communications.length}
                    visibleCount={filters.visibleCommunications.length}
                    pagedCommunications={filters.pagedCommunications}
                    viewMode={viewMode}
                    context={itemContext}
                />
                {!loading && !error && filters.visibleCommunications.length > 0 && (
                    <CommunicationsPagination
                        currentPage={filters.currentPage}
                        totalPages={Math.max(filters.totalPages, 1)}
                        totalItems={filters.visibleCommunications.length}
                        pageSize={COMMUNICATIONS_PAGE_SIZE}
                        onPageChange={filters.setCurrentPage}
                    />
                )}
            </div>

            <CommunicationEditorDialog
                open={editorOpen}
                onClose={() => setEditorOpen(false)}
                onSaved={() => { setEditorOpen(false); loadCommunications(); }}
                communication={editingCommunication}
                scope={activeTab as CommunicationScope}
            />
        </>
    );
};

export default CommunicationsTab;
