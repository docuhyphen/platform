import {useCallback, useEffect, useState} from "react";
import {
    Button,
    MessageBar,
    MessageBarBody,
    Spinner,
    Text,
} from "@fluentui/react-components";
import {BlueprintDefinitionSummaryDto} from "../../models/models.tsx";
import BlueprintEditorDialog from "../../settings/blueprints-tab/BlueprintEditorDialog.tsx";
import {
    deletePlatformBlueprint,
    listPlatformBlueprints,
    setPlatformBlueprintActive,
    setPlatformBlueprintPublished,
} from "../../../services/platformBlueprintService.ts";
import {AddIcon} from "../../components/IconBundles.tsx";
import PlatformBlueprintCard from "./PlatformBlueprintCard.tsx";
import PlatformBlueprintDeleteDialog from "./PlatformBlueprintDeleteDialog.tsx";
import {usePlatformContentStyles} from "./PlatformContentStyles.tsx";

const PlatformBlueprints = () =>
{
    const styles = usePlatformContentStyles();
    const [blueprints, setBlueprints] = useState<BlueprintDefinitionSummaryDto[]>([]);
    const [editing, setEditing] = useState<BlueprintDefinitionSummaryDto | "new" | null>(null);
    const [deleting, setDeleting] = useState<BlueprintDefinitionSummaryDto | null>(null);
    const [deletePending, setDeletePending] = useState(false);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);
    const load = useCallback(async () =>
    {
        setLoading(true);
        setError(null);
        try
        {
            setBlueprints(await listPlatformBlueprints());
        }
        catch (reason: unknown)
        {
            setError(reason instanceof Error ? reason.message : "Failed to load platform Blueprints.");
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
            setError("The platform Blueprint could not be updated.");
        }
    };
    const confirmDelete = async () =>
    {
        if (!deleting) return;
        setDeletePending(true);
        await mutate(() => deletePlatformBlueprint(deleting));
        setDeletePending(false);
        setDeleting(null);
    };

    return (
        <div
            id={"platform-blueprints"}
            className={styles.tabPanel}>
            <div
                id={"platform-blueprints-toolbar"}
                className={styles.actionToolbar}>
                <Button
                    id={"platform-blueprint-create"}
                    appearance={"subtle"}
                    shape={"circular"}
                    icon={<AddIcon/>}
                    onClick={() => setEditing("new")}>
                    Create platform Blueprint
                </Button>
            </div>
            <div
                id={"platform-blueprints-scrollable-content"}
                className={styles.scrollableContent}>
                {error && (
                    <MessageBar
                        id={"platform-blueprints-error"}
                        intent={"error"}>
                        <MessageBarBody id={"platform-blueprints-error-body"}>{error}</MessageBarBody>
                    </MessageBar>
                )}
                {loading ? (
                    <div
                        id={"platform-blueprints-loading"}
                        className={styles.loading}>
                        <Spinner
                            id={"platform-blueprints-spinner"}
                            label={"Loading platform Blueprints"}/>
                    </div>
                ) : blueprints.length === 0 ? (
                    <Text id={"platform-blueprints-empty"}>No platform Blueprints are available.</Text>
                ) : (
                    <div
                        id={"platform-blueprints-grid"}
                        className={styles.grid}>
                        {blueprints.map(blueprint => (
                            <PlatformBlueprintCard
                                key={blueprint.id}
                                blueprint={blueprint}
                                onEdit={() => setEditing(blueprint)}
                                onTogglePublished={() => void mutate(() => setPlatformBlueprintPublished(blueprint))}
                                onToggleActive={() => void mutate(() => setPlatformBlueprintActive(blueprint))}
                                onDelete={() => setDeleting(blueprint)}/>
                        ))}
                    </div>
                )}
            </div>
            <BlueprintEditorDialog
                open={editing !== null}
                blueprint={editing && editing !== "new" ? editing : undefined}
                scope={"APP"}
                enforcedScope={"APP"}
                onClose={() => setEditing(null)}
                onSaved={() =>
                {
                    setEditing(null);
                    void load();
                }}/>
            {deleting && (
                <PlatformBlueprintDeleteDialog
                    blueprint={deleting}
                    deleting={deletePending}
                    onConfirm={() => void confirmDelete()}
                    onDismiss={() => setDeleting(null)}/>
            )}
        </div>
    );
};

export default PlatformBlueprints;
