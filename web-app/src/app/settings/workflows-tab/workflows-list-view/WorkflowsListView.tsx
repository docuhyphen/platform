import {useState} from "react";
import {useAuth} from "../../../../context/AuthContext";
import {WorkflowDefinitionSummaryDto} from "../../../models/models.tsx";
import {useWorkflowsListViewStyles} from "./WorkflowsListViewStyles.tsx";
import WorkflowDeleteDialog from "../workflow-delete-dialog/WorkflowDeleteDialog.tsx";
import WorkflowListControls from "./workflow-list-controls/WorkflowListControls.tsx";
import WorkflowsPagination from "../workflows-pagination/WorkflowsPagination.tsx";
import WorkflowsScopeTabs from "./workflows-scope-tabs/WorkflowsScopeTabs.tsx";
import WorkflowsListContent from "./workflows-list-content/WorkflowsListContent.tsx";
import WorkflowCloneDialog from "./workflow-clone-dialog/WorkflowCloneDialog.tsx";
import {useWorkflowDefinitions} from "./useWorkflowDefinitions.ts";
import {useWorkflowListFilters} from "./useWorkflowListFilters.ts";
import {useWorkflowsViewMode} from "./useWorkflowsViewMode.ts";
import {useWorkflowClone} from "./useWorkflowClone.ts";
import {WORKFLOW_LIST_PAGE_SIZE} from "./workflowListFilters.ts";
import {WorkflowListItemActions, WorkflowListTab} from "./workflowListTypes.ts";

export type {WorkflowListTab} from "./workflowListTypes.ts";

interface Props
{
    activeTab: WorkflowListTab;
    onActiveTabChange: (tab: WorkflowListTab) => void;
    onEdit: (definition: WorkflowDefinitionSummaryDto) => void;
    onNew: (scope: 'PERSONAL' | 'ORG') => void;
}

const WorkflowsListView = ({activeTab, onActiveTabChange, onEdit, onNew}: Props) =>
{
    const styles = useWorkflowsListViewStyles();
    const {appUserPersonOrganization} = useAuth();
    const hasOrg = !!appUserPersonOrganization?.isActive;
    const {viewMode, changeViewMode} = useWorkflowsViewMode();
    const {definitions, loading, error, reload, toggleActive, togglePublished} = useWorkflowDefinitions(activeTab);
    const filters = useWorkflowListFilters(definitions);
    const [deletingDef, setDeletingDef] = useState<WorkflowDefinitionSummaryDto | null>(null);

    const selectTab = (tab: WorkflowListTab) =>
    {
        onActiveTabChange(tab);
        filters.resetFilters();
    };

    const clone = useWorkflowClone(() => selectTab('PERSONAL'));

    const actions: WorkflowListItemActions = {
        onEdit,
        onToggleActive: toggleActive,
        onTogglePublished: togglePublished,
        onClone: clone.openCloneDialog,
        onDelete: setDeletingDef,
    };

    const {visibleDefinitions, currentPage, totalPages} = filters;

    return (
        <>
            <div id={"workflows-list-view-div-7"}
                 className={styles.container}>
                <WorkflowsScopeTabs
                    activeTab={activeTab}
                    hasOrg={hasOrg}
                    onSelectTab={selectTab}
                    onNew={onNew}
                />

                <WorkflowListControls
                    searchQuery={filters.searchQuery}
                    onSearchChange={filters.setSearchQuery}
                    availableTags={filters.availableTags}
                    selectedTags={filters.selectedTags}
                    onTagToggle={filters.toggleTag}
                    onClearTags={filters.clearTags}
                    sortOrder={filters.sortOrder}
                    onSortOrderChange={filters.setSortOrder}
                    viewMode={viewMode}
                    onViewModeChange={changeViewMode}
                />

                <WorkflowsListContent
                    activeTab={activeTab}
                    loading={loading}
                    error={error}
                    totalCount={definitions.length}
                    visibleCount={visibleDefinitions.length}
                    pagedDefinitions={filters.pagedDefinitions}
                    viewMode={viewMode}
                    actions={actions}
                />
                {!loading && !error && visibleDefinitions.length > 0 && (
                    <WorkflowsPagination
                        currentPage={currentPage}
                        totalPages={Math.max(totalPages, 1)}
                        firstItem={currentPage * WORKFLOW_LIST_PAGE_SIZE + 1}
                        lastItem={Math.min((currentPage + 1) * WORKFLOW_LIST_PAGE_SIZE, visibleDefinitions.length)}
                        totalItems={visibleDefinitions.length}
                        itemLabel={activeTab === "APP" ? "templates" : "workflows"}
                        onPageChange={filters.setCurrentPage}
                    />
                )}
            </div>

            {deletingDef && (
                <WorkflowDeleteDialog
                    isOpen={true}
                    definition={deletingDef}
                    onDismiss={() => setDeletingDef(null)}
                    onDeleted={() =>
                    {
                        setDeletingDef(null);
                        reload();
                    }}
                />
            )}

            <WorkflowCloneDialog
                open={!!clone.cloningDef}
                name={clone.cloneNameInput}
                cloning={clone.cloning}
                onNameChange={clone.setCloneNameInput}
                onConfirm={clone.confirmClone}
                onClose={clone.closeCloneDialog}
            />
        </>
    );
};

export default WorkflowsListView;
