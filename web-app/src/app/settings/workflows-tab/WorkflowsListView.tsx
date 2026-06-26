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
    Tab,
    TabList,
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

type ListTab = 'PERSONAL' | 'ORG' | 'APP';

const TAB_LABEL: Record<ListTab, string> = {
    PERSONAL: 'My Workflows',
    ORG: 'Organization',
    APP: 'Platform',
};

interface Props
{
    onEdit: (definition: WorkflowDefinitionSummaryDto) => void;
    onNew: (scope: 'PERSONAL' | 'ORG') => void;
}

const statusColor = (isActive: boolean): "success" | "subtle" => (isActive ? "success" : "subtle");

const WorkflowsListView = ({onEdit, onNew}: Props) =>
{
    const styles = useWorkflowsListViewStyles();
    const [activeTab, setActiveTab] = useState<ListTab>('PERSONAL');
    const [definitions, setDefinitions] = useState<WorkflowDefinitionSummaryDto[]>([]);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [deletingDef, setDeletingDef] = useState<WorkflowDefinitionSummaryDto | null>(null);
    const [cloningDef, setCloningDef] = useState<WorkflowDefinitionSummaryDto | null>(null);
    const [cloneNameInput, setCloneNameInput] = useState("");
    const [cloning, setCloning] = useState(false);

    const load = useCallback(async (tab: ListTab) =>
    {
        setLoading(true);
        setError(null);
        try
        {
            setDefinitions(await listWorkflowDefinitions({scope: tab}));
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
        load(activeTab);
    }, [load, activeTab]);

    const toggleActive = async (def: WorkflowDefinitionSummaryDto) =>
    {
        try
        {
            await patchWorkflowDefinitionStatus(def.id, {isActive: !def.isActive});
            await load(activeTab);
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
            await load(activeTab);
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
            setCloningDef(null);
            // Clones land in PERSONAL; switch there so the user sees the new item.
            setActiveTab('PERSONAL');
        }
        catch
        { /* ignore */
        }
        finally
        {
            setCloning(false);
        }
    };

    const renderRow = (def: WorkflowDefinitionSummaryDto, isPersonal: boolean) => (
        <div key={def.id} className={styles.row}>
            <div className={styles.rowName}>
                <Text weight="semibold">{def.name}</Text>
                {def.summary && <Text size={200} block>{def.summary}</Text>}
                <div className={styles.tagRow}>
                    {def.generalTags.map(t =>
                        <Tag key={t}
                             shape={"circular"}
                             size="extra-small">
                            {t}
                        </Tag>)}
                </div>
            </div>
            <Text className={styles.rowTrigger} size={200}>{formatTriggerName(def.triggerEvent)}</Text>
            {!isPersonal && (
                <Badge
                    color={def.isPublished ? "brand" : "subtle"}
                    appearance={def.isPublished ? "filled" : "outline"}
                    size="small">
                    {def.isPublished ? "Published" : "Draft"}
                </Badge>
            )}
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
                        {!isPersonal && (
                            <MenuItem
                                icon={def.isPublished ? <UnpublishIcon/> : <PublishIcon/>}
                                onClick={() => togglePublished(def)}>
                                {def.isPublished ? "Unpublish" : "Publish"}
                            </MenuItem>
                        )}
                        <MenuItem
                            icon={def.isActive ? <DeactivateIcon/> : <ActivateIcon/>}
                            onClick={() => toggleActive(def)}>
                            {def.isActive ? "Deactivate" : "Activate"}
                        </MenuItem>
                        <MenuItem onClick={() => openCloneDialog(def)} icon={<CopyIcon/>}>Duplicate</MenuItem>
                        <MenuItem icon={<DeleteIcon/>} onClick={() => setDeletingDef(def)}>
                            Delete
                        </MenuItem>
                    </MenuList>
                </MenuPopover>
            </Menu>
        </div>
    );

    const renderPlatformCard = (def: WorkflowDefinitionSummaryDto) => (
        <div key={def.id} className={styles.templateCard}>
            <div className={styles.templateCardInfo}>
                <Text weight="semibold">{def.name}</Text>
                {def.summary && <Text size={200} block>{def.summary}</Text>}
                <Text size={200} block style={{marginTop: "2px"}}>
                    Runs when: {formatTriggerName(def.triggerEvent)}
                </Text>
                <div className={styles.tagRow}>
                    {def.generalTags.map(t =>
                        <Tag key={t}
                             shape={"circular"}
                             size="extra-small">
                            {t}
                        </Tag>)
                    }
                </div>
            </div>
            <Button size="small" appearance="outline" onClick={() => openCloneDialog(def)}>
                Add to my workflows
            </Button>
        </div>
    );

    return (
        <>
            <div>
                <div style={{display: "flex", alignItems: "center", justifyContent: "space-between", marginBottom: "0.75rem"}}>
                    <TabList
                        selectedValue={activeTab}
                        onTabSelect={(_, d) => setActiveTab(d.value as ListTab)}
                    >
                        <Tab value="PERSONAL">{TAB_LABEL.PERSONAL}</Tab>
                        <Tab value="ORG">{TAB_LABEL.ORG}</Tab>
                        <Tab value="APP">{TAB_LABEL.APP}</Tab>
                    </TabList>

                    {activeTab !== 'APP' && (
                        <Button appearance="subtle" icon={<AddIcon/>}
                                onClick={() => onNew(activeTab)}
                                shape="circular">
                            Create Workflow
                        </Button>
                    )}
                </div>

                {error && (
                    <MessageBar intent="error" className={styles.errorBar}>
                        <MessageBarBody>{error}</MessageBarBody>
                    </MessageBar>
                )}

                {loading && <Spinner size="small" label="Loading workflows..."/>}

                {!loading && !error && definitions.length === 0 && (
                    <div className={styles.emptyState}>
                        <Text>
                            {activeTab === 'PERSONAL' && "No personal workflows yet. Create one to get started."}
                            {activeTab === 'ORG' && "No organisation workflows yet. Create one to get started."}
                            {activeTab === 'APP' && "No platform templates available."}
                        </Text>
                    </div>
                )}

                {!loading && !error && definitions.length > 0 && (
                    <div className={styles.cardGrid}>
                        {definitions.map(def =>
                            activeTab === 'APP'
                                ? renderPlatformCard(def)
                                : renderRow(def, activeTab === 'PERSONAL'),
                        )}
                    </div>
                )}
            </div>

            {deletingDef && (
                <WorkflowDeleteDialog
                    isOpen={true}
                    definition={deletingDef}
                    onDismiss={() => setDeletingDef(null)}
                    onDeleted={() =>
                    {
                        setDeletingDef(null);
                        load(activeTab);
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
