import {WorkflowDefinitionSummaryDto} from "../../../../models/models.tsx";
import {WorkflowListItemActions, WorkflowListTab} from "../workflowListTypes.ts";
import WorkflowTableRow from "./workflow-table-row/WorkflowTableRow.tsx";
import {useWorkflowTableStyles} from "./WorkflowTableStyles.tsx";

interface Props
{
    definitions: WorkflowDefinitionSummaryDto[];
    activeTab: WorkflowListTab;
    actions: WorkflowListItemActions;
}

const WorkflowTable = ({definitions, activeTab, actions}: Props) =>
{
    const styles = useWorkflowTableStyles();

    return (
        <table id={"workflows-list-view-table-1"}
               className={styles.table}>
            <thead id={"workflows-list-view-thead-1"}>
                <tr id={"workflows-list-view-tr-1"}>
                    <th id={"workflows-list-view-th-1"}
                        className={styles.th}>Name</th>
                    <th id={"workflows-list-view-th-2"}
                        className={styles.th}>Trigger</th>
                    <th id={"workflows-list-view-th-3"}
                        className={styles.th}>Tags</th>
                    {activeTab !== 'PERSONAL' && <th id={"workflows-list-view-th-4"}
                                                     className={styles.th}>Published</th>}
                    <th id={"workflows-list-view-th-5"}
                        className={styles.th}>Active</th>
                    <th id={"workflows-list-view-th-6"}
                        className={styles.th}/>
                </tr>
            </thead>
            <tbody id={"workflows-list-view-tbody-1"}>
                {definitions.map(def => (
                    <WorkflowTableRow
                        key={def.id}
                        def={def}
                        activeTab={activeTab}
                        actions={actions}
                    />
                ))}
            </tbody>
        </table>
    );
};

export default WorkflowTable;
