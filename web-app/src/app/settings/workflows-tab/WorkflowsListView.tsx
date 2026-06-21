import {useCallback, useEffect, useState} from "react";
import {
    Badge,
    Button,
    Dialog,
    DialogActions,
    DialogBody,
    DialogContent,
    DialogSurface,
    DialogTitle,
    Input,
    Label,
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
    listWorkflowDefinitions,
    patchWorkflowDefinitionPublished,
    patchWorkflowDefinitionStatus,
} from "../../../services/workflowService.ts";
import {useWorkflowsListViewStyles} from "./WorkflowsListViewStyles.tsx";
import {ActivateIcon, AddIcon, CopyIcon, DeactivateIcon, DeleteIcon, EditIcon, PublishIcon, UnpublishIcon} from "../../components/IconBundles.tsx";
import {formatTriggerName} from "./workflowUtils.ts";
import WorkflowDeleteDialog from "./WorkflowDeleteDialog.tsx";

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
    const [deletingDef, setDeletingDef] = useState<WorkflowDefinitionSummaryDto | null>(null);
    const [cloningDef, setCloningDef] = useState<WorkflowDefinitionSummaryDto | null>(null);
    const [cloneNameInput, setCloneNameInput] = useState("");
    const [cloning, setCloning] = useState(false);

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


    const togglePublished = async (def: WorkflowDefinitionSummaryDto) =>
    {
        try
        {
            await patchWorkflowDefinitionPublished(def.id, {isPublished: !def.isPublished});
            await load();
        }
        catch
        { /* ignore */
        }
    };

    const openCloneDialog = (def: WorkflowDefinitionSummaryDto) =>
    {
        setCloningDef(def);
        setCloneNameInput(`${def.name} (copy)`);
    };

    const confirmClone = async () =>
    {
        if (!cloningDef) return;
        setCloning(true);
        try
        {
            await cloneWorkflowDefinition(cloningDef.id, {newName: cloneNameInput.trim() || undefined});
            await load();
            setCloningDef(null);
        }
        catch
        { /* ignore */
        }
        finally
        {
            setCloning(false);
        }
    };

    const myWorkflows = definitions.filter(d => !d.isTemplate);
    const templates = definitions.filter(d => d.isTemplate);

    if (loading) return <Spinner size="small" label="Loading workflows..."/>;

    return (
        <>
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
                            <Badge
                                color={def.isPublished ? "brand" : "subtle"}
                                appearance={def.isPublished ? "filled" : "outline"}
                                size="small">
                                {def.isPublished ? "Published" : "Draft"}
                            </Badge>
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
                                        <MenuItem
                                            icon={def.isPublished ? <UnpublishIcon/> : <PublishIcon/>}
                                            onClick={() => togglePublished(def)}>
                                            {def.isPublished ? "Unpublish" : "Publish"}
                                        </MenuItem>
                                        <MenuItem
                                            icon={def.isActive ? <DeactivateIcon/> : <ActivateIcon/>}
                                            onClick={() => toggleActive(def)}>
                                            {def.isActive ? "Deactivate" : "Activate"}
                                        </MenuItem>
                                        <MenuItem onClick={() => openCloneDialog(def)}
                                                  icon={<CopyIcon/>}>Duplicate</MenuItem>
                                        <MenuItem icon={<DeleteIcon/>} onClick={() => setDeletingDef(def)}>
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
                            <Button size="small" appearance="outline" onClick={() => openCloneDialog(def)}>
                                Add to my workflows
                            </Button>
                        </div>
                    ))}
                </section>
            </div>
            {deletingDef && (
                <WorkflowDeleteDialog
                    isOpen={true}
                    definition={deletingDef}
                    onDismiss={() => setDeletingDef(null)}
                    onDeleted={() =>
                    {
                        setDeletingDef(null);
                        load();
                    }}
                />
            )}

            <Dialog open={!!cloningDef} onOpenChange={(_, d) => { if (!d.open) setCloningDef(null); }}>
                <DialogSurface>
                    <DialogBody>
                        <DialogTitle>Clone workflow</DialogTitle>
                        <DialogContent>
                            <Label htmlFor="clone-name-input" style={{display: "block", marginBottom: "0.25rem"}}>
                                Name
                            </Label>
                            <Input
                                id="clone-name-input"
                                value={cloneNameInput}
                                onChange={(_, d) => setCloneNameInput(d.value)}
                                onKeyDown={e => { if (e.key === "Enter") confirmClone(); }}
                                style={{width: "100%"}}
                                autoFocus
                            />
                        </DialogContent>
                        <DialogActions>
                            <Button appearance="secondary" shape="circular"
                                    onClick={() => setCloningDef(null)}>
                                Cancel
                            </Button>
                            <Button appearance="primary" shape="circular"
                                    onClick={confirmClone}
                                    disabled={cloning || !cloneNameInput.trim()}>
                                {cloning ? "Cloning…" : "Clone"}
                            </Button>
                        </DialogActions>
                    </DialogBody>
                </DialogSurface>
            </Dialog>
        </>
    );
};

export default WorkflowsListView;




