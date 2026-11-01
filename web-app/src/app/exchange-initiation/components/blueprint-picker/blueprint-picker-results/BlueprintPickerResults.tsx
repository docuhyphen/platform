import React from 'react';
import {Spinner, Text} from '@fluentui/react-components';
import {BlueprintDefinitionSummaryDto} from '../../../../models/models.tsx';
import {useExchangeInitiationStyles} from '../../../ExchangeInitiationStyles.tsx';
import ExchangeListPagination
    from '../../../../exchanges/components/exchange-list/exchange-list-pagination/ExchangeListPagination.tsx';
import BlueprintCard from '../blueprint-card/BlueprintCard.tsx';

interface BlueprintPickerResultsProps
{
    loading: boolean;
    error: string | null;
    hasBlueprints: boolean;
    hasMatches: boolean;
    visibleBlueprints: BlueprintDefinitionSummaryDto[];
    totalPages: number;
    currentPage: number;
    onPageChange: (page: number) => void;
    onSelect: (blueprint: BlueprintDefinitionSummaryDto) => void;
}

const BlueprintPickerResults: React.FC<BlueprintPickerResultsProps> = (
    {
        loading,
        error,
        hasBlueprints,
        hasMatches,
        visibleBlueprints,
        totalPages,
        currentPage,
        onPageChange,
        onSelect,
    }) =>
{
    const styles = useExchangeInitiationStyles();

    return (
        <>
            {loading && (
                <div id={"blueprint-picker-spinner-wrapper"}
                     className={styles.blueprintPickerSpinnerWrapper}>
                    <Spinner id={"blueprint-picker-loading-blueprints-spinner"}
                             size="small"
                             label="Loading blueprints..."/>
                </div>
            )}

            {!loading && error && (
                <Text id={"blueprint-picker-error-text"}
                      className={styles.blueprintPickerErrorText}>{error}</Text>
            )}

            {!loading && !error && !hasBlueprints && (
                <Text id={"blueprint-picker-empty-text"}
                      className={styles.blueprintPickerEmptyText}>
                    No blueprints available in this category.
                </Text>
            )}

            {!loading && !error && hasBlueprints && !hasMatches && (
                <Text id={"blueprint-picker-empty-text-2"}
                      className={styles.blueprintPickerEmptyText}>
                    No blueprints match your search.
                </Text>
            )}

            {!loading && !error && visibleBlueprints.length > 0 && (
                <div id={"blueprint-picker-blueprint-list"}
                     className={styles.blueprintList}>
                    {visibleBlueprints.map(bp => (
                        <BlueprintCard key={bp.id}
                                       blueprint={bp}
                                       onSelect={onSelect}/>
                    ))}
                </div>
            )}

            {!loading && !error && totalPages > 1 && (
                <div id={"blueprint-picker-pagination-row"}
                     className={styles.blueprintPickerPaginationRow}>
                    <ExchangeListPagination
                        currentPage={currentPage}
                        totalPages={totalPages}
                        onPageChange={onPageChange}
                    />
                </div>
            )}
        </>
    );
};

export default BlueprintPickerResults;
