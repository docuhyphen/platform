import {MessageBar, MessageBarBody, Spinner, Text} from "@fluentui/react-components";
import {ViewMode, WorkflowDefinitionSummaryDto} from "../../../../models/models.tsx";
import WorkflowCardGrid from "../workflow-card-grid/WorkflowCardGrid.tsx";
import WorkflowTable from "../workflow-table/WorkflowTable.tsx";
import {WorkflowListItemActions, WorkflowListTab} from "../workflowListTypes.ts";
import {useWorkflowsListContentStyles} from "./WorkflowsListContentStyles.tsx";

interface Props
{
    activeTab: WorkflowListTab;
    loading: boolean;
    error: string | null;
    totalCount: number;
    visibleCount: number;
    pagedDefinitions: WorkflowDefinitionSummaryDto[];
    viewMode: ViewMode;
    actions: WorkflowListItemActions;
}

const EMPTY_MESSAGE: Record<WorkflowListTab, string> = {
    PERSONAL: "No personal workflows yet. Create one to get started.",
    ORG: "No organisation workflows yet. Create one to get started.",
    APP: "No platform templates available.",
};

const WorkflowsListContent = ({activeTab, loading, error, totalCount, visibleCount, pagedDefinitions, viewMode, actions}: Props) =>
{
    const styles = useWorkflowsListContentStyles();
    const ready = !loading && !error;

    return (
        <div id={"workflows-list-view-div-9"}
             className={styles.scrollableContent}>
            {error && (
                <MessageBar id={"workflows-list-view-message-bar-1"}
                            intent="error"
                            className={styles.errorBar}>
                    <MessageBarBody id={"workflows-list-view-message-bar-body-1"}>{error}</MessageBarBody>
                </MessageBar>
            )}

            {loading && <Spinner id={"workflows-list-view-spinner-1"}
                                 size="small"
                                 label="Loading workflows..."/>}

            {ready && totalCount === 0 && (
                <div id={"workflows-list-view-div-10"}
                     className={styles.emptyState}>
                    <Text id={"workflows-list-view-text-7"}>{EMPTY_MESSAGE[activeTab]}</Text>
                </div>
            )}

            {ready && totalCount > 0 && visibleCount === 0 && (
                <div id={"workflows-list-view-div-11"}
                     className={styles.emptyState}>
                    <Text id={"workflows-list-view-text-8"}>No workflows match your search and filters.</Text>
                </div>
            )}

            {ready && pagedDefinitions.length > 0 && viewMode === 'cards' && (
                <WorkflowCardGrid
                    definitions={pagedDefinitions}
                    activeTab={activeTab}
                    actions={actions}
                />
            )}

            {ready && pagedDefinitions.length > 0 && viewMode === 'table' && (
                <WorkflowTable
                    definitions={pagedDefinitions}
                    activeTab={activeTab}
                    actions={actions}
                />
            )}
        </div>
    );
};

export default WorkflowsListContent;
