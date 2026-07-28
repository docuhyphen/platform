import {useCallback, useEffect, useState} from "react";
import {Button, MessageBar, MessageBarBody, Spinner, Text} from "@fluentui/react-components";
import {CommunicationDto, CommunicationSummaryDto} from "../../models/models.tsx";
import CommunicationEditorDialog from "../../settings/communications-tab/CommunicationEditorDialog.tsx";
import {deletePlatformCommunication, getPlatformCommunication, listPlatformCommunications, setPlatformCommunicationActive, setPlatformCommunicationPublished} from "../../../services/platformCommunicationService.ts";
import {AddIcon} from "../../components/IconBundles.tsx";
import PlatformCommunicationCard from "./PlatformCommunicationCard.tsx";
import PlatformCommunicationDeleteDialog from "./PlatformCommunicationDeleteDialog.tsx";
import {usePlatformContentStyles} from "./PlatformContentStyles.tsx";
const PlatformCommunications = () => {
    const styles = usePlatformContentStyles();
    const [communications, setCommunications] = useState<CommunicationSummaryDto[]>([]);
    const [editing, setEditing] = useState<CommunicationDto | "new" | null>(null);
    const [deleting, setDeleting] = useState<CommunicationSummaryDto | null>(null);
    const [deletePending, setDeletePending] = useState(false);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);
    const load = useCallback(async () =>
    {
        setLoading(true);
        setError(null);
        try
        {
            setCommunications(await listPlatformCommunications());
        }
        catch (reason: unknown)
        {
            setError(reason instanceof Error ? reason.message : "Failed to load platform communications.");
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
        try
        {
            await action();
            await load();
        }
        catch
        {
            setError("The platform communication could not be updated.");
        }
    };
    const edit = async (communication: CommunicationSummaryDto) =>
    {
        setError(null);
        try
        {
            setEditing(await getPlatformCommunication(communication));
        }
        catch
        {
            setError("The platform communication could not be opened.");
        }
    };
    const confirmDelete = async () =>
    {
        if (!deleting) return;
        setDeletePending(true);
        await mutate(() => deletePlatformCommunication(deleting));
        setDeletePending(false);
        setDeleting(null);
    };
    return (
        <div
            id={"platform-communications"}
            className={styles.tabPanel}>
            <div
                id={"platform-communications-toolbar"}
                className={styles.actionToolbar}>
                <Button
                    id={"platform-communication-create"}
                    appearance={"subtle"}
                    shape={"circular"}
                    icon={<AddIcon/>}
                    onClick={() => setEditing("new")}>
                    Create platform communication
                </Button>
            </div>
            <div
                id={"platform-communications-scrollable-content"}
                className={styles.scrollableContent}>
                {error && (
                    <MessageBar
                        id={"platform-communications-error"}
                        intent={"error"}>
                        <MessageBarBody id={"platform-communications-error-body"}>{error}</MessageBarBody>
                    </MessageBar>
                )}
                {loading ? (
                    <div
                        id={"platform-communications-loading"}
                        className={styles.loading}>
                        <Spinner
                            id={"platform-communications-spinner"}
                            label={"Loading platform communications"}/>
                    </div>
                ) : communications.length === 0 ? (
                    <Text id={"platform-communications-empty"}>No platform communications are available.</Text>
                ) : (
                    <div
                        id={"platform-communications-grid"}
                        className={styles.grid}>
                        {communications.map(communication => (
                            <PlatformCommunicationCard
                                key={communication.id}
                                communication={communication}
                                onEdit={() => void edit(communication)}
                                onTogglePublished={() => void mutate(
                                    () => setPlatformCommunicationPublished(communication),
                                )}
                                onToggleActive={() => void mutate(
                                    () => setPlatformCommunicationActive(communication),
                                )}
                                onDelete={() => setDeleting(communication)}/>
                        ))}
                    </div>
                )}
            </div>
            <CommunicationEditorDialog
                open={editing !== null}
                communication={editing && editing !== "new" ? editing : undefined}
                scope={"PLATFORM"}
                enforcedScope={"PLATFORM"}
                createAsTemplate={true}
                onClose={() => setEditing(null)}
                onSaved={() =>
                {
                    setEditing(null);
                    void load();
                }}/>
            {deleting && (
                <PlatformCommunicationDeleteDialog
                    communication={deleting}
                    deleting={deletePending}
                    onConfirm={() => void confirmDelete()}
                    onDismiss={() => setDeleting(null)}/>
            )}
        </div>
    );
};
export default PlatformCommunications;
