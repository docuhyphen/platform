import {Text} from '@fluentui/react-components';
import {BlueprintDefinitionSummaryDto, ViewMode} from '../../../models/models.tsx';
import {BlueprintListItemContext} from '../blueprintListTypes.ts';
import {blueprintEmptyMessages} from '../blueprintListFilters.ts';
import BlueprintCardGrid from '../blueprint-card-grid/BlueprintCardGrid.tsx';
import BlueprintTable from '../blueprint-table/BlueprintTable.tsx';
import {useBlueprintsListContentStyles} from './BlueprintsListContentStyles.tsx';

interface BlueprintsListContentProps
{
    loading: boolean;
    error: string | null;
    hasBlueprints: boolean;
    filteredCount: number;
    visibleBlueprints: BlueprintDefinitionSummaryDto[];
    viewMode: ViewMode;
    context: BlueprintListItemContext;
}

const BlueprintsListContent = (
    {
        loading,
        error,
        hasBlueprints,
        filteredCount,
        visibleBlueprints,
        viewMode,
        context,
    }: BlueprintsListContentProps) =>
{
    const styles = useBlueprintsListContentStyles();
    const ready = !loading && !error;

    return (
        <div id={"blueprints-tab-div-8"}
             className={styles.scrollableContent}>
            {!loading && error && (
                <Text id={"blueprints-tab-text-2"}
                      className={styles.errorText}>{error}</Text>
            )}
            {ready && !hasBlueprints && (
                <Text id={"blueprints-tab-text-3"}
                      className={styles.emptyText}>{blueprintEmptyMessages[context.activeTab]}</Text>
            )}
            {ready && hasBlueprints && filteredCount === 0 && (
                <Text id={"blueprints-tab-text-4"}
                      className={styles.emptyText}>No blueprints match your search.</Text>
            )}
            {ready && visibleBlueprints.length > 0 && viewMode === 'cards' && (
                <BlueprintCardGrid blueprints={visibleBlueprints}
                                   context={context}/>
            )}
            {ready && visibleBlueprints.length > 0 && viewMode === 'table' && (
                <BlueprintTable blueprints={visibleBlueprints}
                                context={context}/>
            )}
        </div>
    );
};

export default BlueprintsListContent;
