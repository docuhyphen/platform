import {useState} from 'react';
import {Spinner} from '@fluentui/react-components';
import {useTemplatesTabStyles} from './BlueprintsTabStyles.tsx';
import {BlueprintDefinitionSummaryDto, BlueprintScope, Capability} from '../../models/models.tsx';
import BlueprintEditorDialog from './BlueprintEditorDialog.tsx';
import {useAuth} from '../../../context/AuthContext.tsx';
import BlueprintsPagination from "./blueprints-pagination/BlueprintsPagination.tsx";
import BlueprintsScopeTabs from './blueprints-scope-tabs/BlueprintsScopeTabs.tsx';
import BlueprintsToolbar from './blueprints-toolbar/BlueprintsToolbar.tsx';
import BlueprintsActiveTags from './blueprints-active-tags/BlueprintsActiveTags.tsx';
import BlueprintsListContent from './blueprints-list-content/BlueprintsListContent.tsx';
import {BlueprintListItemContext, BlueprintListTab} from './blueprintListTypes.ts';
import {BLUEPRINTS_PAGE_SIZE} from './blueprintListFilters.ts';
import {createBlueprintListActions} from './blueprintListActions.ts';
import {useBlueprintsLoader} from './useBlueprintsLoader.ts';
import {useBlueprintListFilters} from './useBlueprintListFilters.ts';
import {useBlueprintsViewMode} from './useBlueprintsViewMode.ts';

const BlueprintsTab = () =>
{
    const styles = useTemplatesTabStyles();
    const {appUserPersonOrganization, hasCapability} = useAuth();

    const hasOrg = !!appUserPersonOrganization?.isActive;
    const canManageOrganization = hasOrg && hasCapability(Capability.ORG_POLICY_MANAGE);

    const [viewMode, changeViewMode] = useBlueprintsViewMode();
    const [activeTab, setActiveTab] = useState<BlueprintListTab>('PERSONAL');
    const [editorOpen, setEditorOpen] = useState(false);
    const [editingBlueprint, setEditingBlueprint] = useState<BlueprintDefinitionSummaryDto | undefined>();
    const {blueprints, loading, error, reload, clear} = useBlueprintsLoader(activeTab);
    const filters = useBlueprintListFilters(blueprints);

    const openEdit = (bp: BlueprintDefinitionSummaryDto) =>
    {
        setEditingBlueprint(bp);
        setEditorOpen(true);
    };

    const openCreate = () =>
    {
        setEditingBlueprint(undefined);
        setEditorOpen(true);
    };

    const selectTab = (tab: BlueprintListTab) =>
    {
        setActiveTab(tab);
        clear();
        filters.reset();
    };

    const canCreate =
        activeTab === 'PERSONAL' ||
        (activeTab === 'ORG' && canManageOrganization);

    const itemContext: BlueprintListItemContext = {
        activeTab,
        canManageOrganization,
        canManageItem: bp =>
            bp.scope === 'PERSONAL' ||
            (bp.scope === 'ORG' && canManageOrganization),
        actions: createBlueprintListActions(reload, openEdit),
    };

    return (
        <>
            <div id={"blueprints-tab-div-1"}
                 className={styles.outerContainer}>
                <div id={"blueprints-tab-div-2"}
                     className={styles.stickyBlock}>
                    <BlueprintsScopeTabs activeTab={activeTab}
                                         hasOrg={hasOrg}
                                         canCreate={canCreate}
                                         onTabSelect={selectTab}
                                         onCreate={openCreate}/>

                    <BlueprintsToolbar filters={filters}
                                       viewMode={viewMode}
                                       onViewModeChange={changeViewMode}/>

                    {filters.selectedTags.size > 0 && (
                        <BlueprintsActiveTags selectedTags={filters.selectedTags}
                                              onRemoveTag={filters.removeTag}
                                              onClearTags={filters.clearTags}/>
                    )}
                </div>

                {loading && <Spinner id={"blueprints-tab-spinner-1"}
                                     size="small"
                                     label="Loading blueprints..."/>}
                <BlueprintsListContent loading={loading}
                                       error={error}
                                       hasBlueprints={blueprints.length > 0}
                                       filteredCount={filters.filteredBlueprints.length}
                                       visibleBlueprints={filters.visibleBlueprints}
                                       viewMode={viewMode}
                                       context={itemContext}/>
                {!loading && !error && filters.filteredBlueprints.length > 0 && (
                    <BlueprintsPagination
                        currentPage={filters.currentPage}
                        totalPages={Math.max(filters.totalPages, 1)}
                        totalItems={filters.filteredBlueprints.length}
                        pageSize={BLUEPRINTS_PAGE_SIZE}
                        onPageChange={filters.setCurrentPage}
                    />
                )}
            </div>

            <BlueprintEditorDialog
                open={editorOpen}
                onClose={() => setEditorOpen(false)}
                onSaved={() =>
                {
                    setEditorOpen(false);
                    reload();
                }}
                blueprint={editingBlueprint}
                scope={activeTab as BlueprintScope}
            />
        </>
    );
};

export default BlueprintsTab;
