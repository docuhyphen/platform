import {WorkflowDefinitionSummaryDto} from "../../../../models/models.tsx";
import WorkflowCard from "../workflow-card/WorkflowCard.tsx";
import WorkflowPlatformCard from "../workflow-platform-card/WorkflowPlatformCard.tsx";
import {WorkflowListItemActions, WorkflowListTab} from "../workflowListTypes.ts";
import {useWorkflowCardGridStyles} from "./WorkflowCardGridStyles.tsx";

interface Props
{
    definitions: WorkflowDefinitionSummaryDto[];
    activeTab: WorkflowListTab;
    actions: WorkflowListItemActions;
}

const WorkflowCardGrid = ({definitions, activeTab, actions}: Props) =>
{
    const styles = useWorkflowCardGridStyles();

    return (
        <div id={"workflows-list-view-div-12"}
             className={styles.cardGrid}>
            {definitions.map(def =>
                activeTab === 'APP'
                    ? (
                        <WorkflowPlatformCard
                            key={def.id}
                            def={def}
                            onAdd={() => actions.onClone(def)}
                        />
                    )
                    : (
                        <WorkflowCard
                            key={def.id}
                            def={def}
                            isPersonal={activeTab === 'PERSONAL'}
                            onEdit={() => actions.onEdit(def)}
                            onToggleActive={() => actions.onToggleActive(def)}
                            onTogglePublished={() => actions.onTogglePublished(def)}
                            onClone={() => actions.onClone(def)}
                            onDelete={() => actions.onDelete(def)}
                        />
                    ),
            )}
        </div>
    );
};

export default WorkflowCardGrid;
