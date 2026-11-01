import {Button, Text} from "@fluentui/react-components";
import TagList from "../../../../components/TagList.tsx";
import {WorkflowDefinitionSummaryDto} from "../../../../models/models.tsx";
import {formatTriggerName} from "../../workflowUtils.ts";
import {useWorkflowPlatformCardStyles} from "./WorkflowPlatformCardStyles.tsx";

interface Props
{
    def: WorkflowDefinitionSummaryDto;
    onAdd: () => void;
}

const WorkflowPlatformCard = ({def, onAdd}: Props) =>
{
    const styles = useWorkflowPlatformCardStyles();

    return (
        <div id={`workflows-list-view-div-5-${def.id}`}
             className={styles.templateCard}>
            <div id={"workflows-list-view-div-6"}
                 className={styles.templateCardInfo}>
                <Text id={"workflows-list-view-text-4"}
                      weight="semibold">{def.name}</Text>
                {def.summary && <Text id={"workflows-list-view-text-5"}
                                      size={200}
                                      block>{def.summary}</Text>}
                <Text
                    id={"workflows-list-view-text-6"}
                    size={200}
                    block
                    className={styles.platformCardTriggerText}
                >
                    Runs when: {formatTriggerName(def.triggerEvent)}
                </Text>
                <TagList tags={def.generalTags}/>
            </div>
            <Button
                id={`workflows-list-add-platform-btn-${def.id}`}
                size="small"
                appearance="outline"
                shape={"circular"}
                onClick={onAdd}
            >
                Add to my workflows
            </Button>
        </div>
    );
};

export default WorkflowPlatformCard;
