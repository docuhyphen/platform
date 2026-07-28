import {useCallback, useEffect, useState} from "react";
import {
    Button,
    MessageBar,
    MessageBarBody,
    Spinner,
    Text,
} from "@fluentui/react-components";
import {WorkflowDefinitionSummaryDto} from "../../models/models.tsx";
import {
    listPlatformWorkflowTemplates,
    setPlatformWorkflowActive,
    setPlatformWorkflowPublished,
} from "../../../services/platformWorkflowService.ts";
import WorkflowDeleteDialog from "../../settings/workflows-tab/WorkflowDeleteDialog.tsx";
import {AddIcon} from "../../components/IconBundles.tsx";
import PlatformWorkflowTemplateCard from "./PlatformWorkflowTemplateCard.tsx";
import {usePlatformContentStyles} from "./PlatformContentStyles.tsx";

interface Props
{
    onCreate: () => void;
    onEdit: (definition: WorkflowDefinitionSummaryDto) => void;
}

const PlatformWorkflowTemplates = ({onCreate, onEdit}: Props) =>
{
    const styles = usePlatformContentStyles();
    const [definitions, setDefinitions] = useState<WorkflowDefinitionSummaryDto[]>([]);
    const [deleting, setDeleting] = useState<WorkflowDefinitionSummaryDto | null>(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);

    const load = useCallback(async () =>
    {
        setLoading(true);
        setError(null);
        try
        {
            setDefinitions(await listPlatformWorkflowTemplates());
        }
        catch (reason: unknown)
        {
            setError(reason instanceof Error ? reason.message : "Failed to load platform workflows.");
        }
        finally
        {
            setLoading(false);
        }
    }, []);

    useEffect(() =>
    {
        void load();
    }, [load]);

    const mutate = async (action: () => Promise<unknown>) =>
    {
        setError(null);
        try
        {
            await action();
            await load();
        }
        catch
        {
            setError("The platform workflow could not be updated.");
        }
    };

    return (
        <div
            id={"platform-workflow-templates"}
            className={styles.tabPanel}>
            <div
                id={"platform-workflow-templates-toolbar"}
                className={styles.actionToolbar}>
                <Button
                    id={"platform-workflow-template-create"}
                    appearance={"subtle"}
                    shape={"circular"}
                    icon={<AddIcon/>}
                    onClick={onCreate}>
                    Create platform workflow
                </Button>
            </div>
            <div
                id={"platform-workflow-templates-scrollable-content"}
                className={styles.scrollableContent}>
                {error && (
                    <MessageBar
                        id={"platform-workflow-templates-error"}
                        intent={"error"}>
                        <MessageBarBody id={"platform-workflow-templates-error-body"}>
                            {error}
                        </MessageBarBody>
                    </MessageBar>
                )}
                {loading ? (
                    <div
                        id={"platform-workflow-templates-loading"}
                        className={styles.loading}>
                        <Spinner
                            id={"platform-workflow-templates-spinner"}
                            label={"Loading platform workflows"}/>
                    </div>
                ) : definitions.length === 0 ? (
                    <Text id={"platform-workflow-templates-empty"}>
                        No platform workflow templates are available.
                    </Text>
                ) : (
                    <div
                        id={"platform-workflow-templates-grid"}
                        className={styles.grid}>
                        {definitions.map(definition => (
                            <PlatformWorkflowTemplateCard
                                key={definition.id}
                                definition={definition}
                                onEdit={() => onEdit(definition)}
                                onToggleActive={() => void mutate(() => setPlatformWorkflowActive(definition))}
                                onTogglePublished={() => void mutate(() => setPlatformWorkflowPublished(definition))}
                                onDelete={() => setDeleting(definition)}/>
                        ))}
                    </div>
                )}
            </div>
            {deleting && (
                <WorkflowDeleteDialog
                    isOpen={true}
                    definition={deleting}
                    onDismiss={() => setDeleting(null)}
                    onDeleted={() =>
                    {
                        setDeleting(null);
                        void load();
                    }}/>
            )}
        </div>
    );
};

export default PlatformWorkflowTemplates;
