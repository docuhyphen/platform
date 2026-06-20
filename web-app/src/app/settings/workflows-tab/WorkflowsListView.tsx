import {useCallback, useEffect, useState} from "react";
import {
    Badge,
    Button,
    Menu,
    MenuItem,
    MenuList,
    MenuPopover,
    MenuTrigger,
    MessageBar,
    MessageBarBody,
    Spinner,
    Tag,
    Text,
} from "@fluentui/react-components";
import {MoreVerticalRegular} from "@fluentui/react-icons";
import {WorkflowDefinitionSummaryDto} from "../../models/models.tsx";
import {
    cloneWorkflowDefinition,
    deleteWorkflowDefinition,
    listWorkflowDefinitions,
    patchWorkflowDefinitionStatus,
} from "../../../services/workflowService.ts";
import {useWorkflowsListViewStyles} from "./WorkflowsListViewStyles.tsx";
import {AddIcon, DeleteIcon, EditIcon} from "../../components/IconBundles.tsx";
import {formatTriggerName} from "./workflowUtils.ts";

interface Props
{
    onEdit: (definition: WorkflowDefinitionSummaryDto) => void;
    onNew: () => void;
}

const statusColor = (isActive: boolean): "success" | "subtle" => (isActive ? "success" : "subtle");

const WorkflowsListView = ({onEdit, onNew}: Props) =>
{
    const styles = useWorkflowsListViewStyles();
    const [definitions, setDefinitions] = useState<WorkflowDefinitionSummaryDto[]>([]);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<string | null>(null);

    const load = useCallback(async () =>
    {
        setLoading(true);
        setError(null);
        try
        {
            setDefinitions(await listWorkflowDefinitions());
        }
        catch (e: unknown)
        {
            setError(typeof e === "string" ? e : "Failed to load workflows");
        }
        finally
        {
            setLoading(false);
        }
    }, []);

    useEffect(() =>
    {
        load();
    }, [load]);

    const toggleActive = async (def: WorkflowDefinitionSummaryDto) =>
    {
        try
        {
            await patchWorkflowDefinitionStatus(def.id, {isActive: !def.isActive});
            await load();
        }
        catch
        { /* ignore */
        }
    };

    const remove = async (def: WorkflowDefinitionSummaryDto) =>
    {
        try
        {
            await deleteWorkflowDefinition(def.id);
            await load();
        }
        catch
        { /* ignore */
        }
    };

    const clone = async (def: WorkflowDefinitionSummaryDto) =>
    {
        try
        {
            await cloneWorkflowDefinition(def.id, {});
            await load();
        }
        catch
        { /* ignore */
        }
    };

    const myWorkflows = definitions.filter(d => !d.isTemplate);
    const templates = definitions.filter(d => d.isTemplate);

    if (loading) return <Spinner size="small" label="Loading workflows..."/>;

    return (
        <div>
            {error && (
                <MessageBar intent="error" className={styles.errorBar}>
                    <MessageBarBody>{error}</MessageBarBody>
                </MessageBar>
            )}

            {/* My Workflows section */}
            <section className={styles.section}>
                <div className={styles.sectionHeader}>
                    <span></span>
                    <Button appearance="secondary"
                            icon={<AddIcon/>} onClick={onNew}
                            shape={"circular"}>
                        New Workflow
                    </Button>
                </div>

                {myWorkflows.length === 0 && (
                    <div className={styles.emptyState}>
                        <Text>No workflows yet. Create one to get started.</Text>
                    </div>
                )}

                {myWorkflows.map(def => (
                    <div key={def.id} className={styles.row}>
                        <div className={styles.rowName}>
                            <Text weight="semibold">{def.name}</Text>
                            {def.summary && <Text size={200} block>{def.summary}</Text>}
                            <div className={styles.tagRow}>
                                {def.generalTags.map(t => <Tag key={t} size="extra-small">{t}</Tag>)}
                            </div>
                        </div>
                        <Text className={styles.rowTrigger} size={200}>{formatTriggerName(def.triggerEvent)}</Text>
                        <Badge color={statusColor(def.isActive)} appearance="filled" size="small">
                            {def.isActive ? "Active" : "Inactive"}
                        </Badge>
                        <Menu>
                            <MenuTrigger disableButtonEnhancement>
                                <Button size="small" appearance="subtle"
                                        icon={<MoreVerticalRegular/>}
                                        aria-label="More actions"/>
                            </MenuTrigger>
                            <MenuPopover>
                                <MenuList>
                                    <MenuItem icon={<EditIcon/>} onClick={() => onEdit(def)}>
                                        Edit
                                    </MenuItem>
                                    <MenuItem onClick={() => toggleActive(def)}>
                                        {def.isActive ? "Deactivate" : "Activate"}
                                    </MenuItem>
                                    <MenuItem onClick={() => clone(def)}>Duplicate</MenuItem>
                                    <MenuItem icon={<DeleteIcon/>} onClick={() => remove(def)}>
                                        Delete
                                    </MenuItem>
                                </MenuList>
                            </MenuPopover>
                        </Menu>
                    </div>
                ))}
            </section>

            {/* Platform Templates section */}
            <section className={styles.section}>
                <div className={styles.sectionHeader}>
                    <Text size={400} weight="semibold">Platform Templates</Text>
                </div>

                {templates.length === 0 && (
                    <div className={styles.emptyState}>
                        <Text>No platform templates available.</Text>
                    </div>
                )}

                {templates.map(def => (
                    <div key={def.id} className={styles.templateCard}>
                        <div className={styles.templateCardInfo}>
                            <Text weight="semibold">{def.name}</Text>
                            {def.summary && <Text size={200} block>{def.summary}</Text>}
                            <Text size={200} block style={{marginTop: "2px"}}>
                                Runs when: {formatTriggerName(def.triggerEvent)}
                            </Text>
                            <div className={styles.tagRow}>
                                {def.generalTags.map(t => <Tag key={t} size="extra-small">{t}</Tag>)}
                            </div>
                        </div>
                        <Button size="small" appearance="outline" onClick={() => clone(def)}>
                            Add to my workflows
                        </Button>
                    </div>
                ))}
            </section>
        </div>
    );
};

export default WorkflowsListView;




