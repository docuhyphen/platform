import {Spinner, Text} from '@fluentui/react-components';
import {CommunicationSummaryDto, ViewMode} from '../../../models/models';
import {CommunicationListItemContext, CommunicationListTab} from '../communicationListTypes';
import {communicationEmptyMessages} from '../communicationListFilters';
import CommunicationCardGrid from '../communication-card-grid/CommunicationCardGrid';
import CommunicationTable from '../communication-table/CommunicationTable';
import {useCommunicationsListContentStyles} from './CommunicationsListContentStyles';

interface Props
{
    activeTab: CommunicationListTab;
    loading: boolean;
    error: string | null;
    totalCount: number;
    visibleCount: number;
    pagedCommunications: CommunicationSummaryDto[];
    viewMode: ViewMode;
    context: CommunicationListItemContext;
}

const CommunicationsListContent = (props: Props) =>
{
    const {activeTab, loading, error, totalCount, visibleCount, pagedCommunications, viewMode, context} = props;
    const styles = useCommunicationsListContentStyles();
    const ready = !loading && !error;

    return (
        <div id={"communications-tab-div-3"}
             className={styles.scrollableContent}>
            {loading && (
                <Spinner
                    id={"communications-tab-spinner-1"}
                    size="small"
                    label={"Loading communications..."}
                />
            )}
            {!loading && error && (
                <Text id={"communications-tab-text-1"}
                      className={styles.errorText}>{error}</Text>
            )}
            {ready && totalCount === 0 && (
                <Text id={"communications-tab-text-2"}
                      className={styles.emptyText}>{communicationEmptyMessages[activeTab]}</Text>
            )}
            {ready && totalCount > 0 && visibleCount === 0 && (
                <Text id={"communications-tab-text-3"}
                      className={styles.emptyText}>No communications match your search and filters.</Text>
            )}
            {ready && pagedCommunications.length > 0 && viewMode === 'cards' && (
                <CommunicationCardGrid
                    communications={pagedCommunications}
                    context={context}
                />
            )}
            {ready && pagedCommunications.length > 0 && viewMode === 'table' && (
                <CommunicationTable
                    communications={pagedCommunications}
                    context={context}
                />
            )}
        </div>
    );
};

export default CommunicationsListContent;
