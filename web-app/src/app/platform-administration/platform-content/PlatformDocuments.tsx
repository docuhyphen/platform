import {useCallback, useEffect, useState} from "react";
import {Button, MessageBar, MessageBarBody, Spinner, Text} from "@fluentui/react-components";
import {DocumentLibraryEntrySummaryDto} from "../../models/models.tsx";
import {
    deletePlatformDocument,
    downloadPlatformDocumentFile,
    listPlatformDocuments,
    setPlatformDocumentActive,
    setPlatformDocumentPublished,
} from "../../../services/platformDocumentLibraryService.ts";
import {AddIcon} from "../../components/IconBundles.tsx";
import PlatformDocumentCard from "./PlatformDocumentCard.tsx";
import PlatformDocumentDialogs from "./PlatformDocumentDialogs.tsx";
import {usePlatformContentStyles} from "./PlatformContentStyles.tsx";

const PlatformDocuments = () =>
{
    const styles = usePlatformContentStyles();
    const [documents, setDocuments] = useState<DocumentLibraryEntrySummaryDto[]>([]);
    const [editing, setEditing] = useState<DocumentLibraryEntrySummaryDto | "new" | null>(null);
    const [uploading, setUploading] = useState<DocumentLibraryEntrySummaryDto | null>(null);
    const [deleting, setDeleting] = useState<DocumentLibraryEntrySummaryDto | null>(null);
    const [deletePending, setDeletePending] = useState(false);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);

    const load = useCallback(async () =>
    {
        setLoading(true);
        setError(null);
        try
        {
            setDocuments(await listPlatformDocuments());
        }
        catch (reason: unknown)
        {
            setError(reason instanceof Error ? reason.message : "Failed to load platform documents.");
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
            setError("The platform document could not be updated.");
        }
    };

    const confirmDelete = async () =>
    {
        if (!deleting) return;
        setDeletePending(true);
        await mutate(() => deletePlatformDocument(deleting));
        setDeletePending(false);
        setDeleting(null);
    };

    return (
        <div
            id={"platform-documents"}
            className={styles.tabPanel}>
            <div
                id={"platform-documents-toolbar"}
                className={styles.actionToolbar}>
                <Button
                    id={"platform-document-create"}
                    appearance={"subtle"}
                    shape={"circular"}
                    icon={<AddIcon/>}
                    onClick={() => setEditing("new")}>
                    Create platform document
                </Button>
            </div>
            <div
                id={"platform-documents-scrollable-content"}
                className={styles.scrollableContent}>
                {error && (
                    <MessageBar
                        id={"platform-documents-error"}
                        intent={"error"}>
                        <MessageBarBody id={"platform-documents-error-body"}>{error}</MessageBarBody>
                    </MessageBar>
                )}
                {loading ? (
                    <div
                        id={"platform-documents-loading"}
                        className={styles.loading}>
                        <Spinner
                            id={"platform-documents-spinner"}
                            label={"Loading platform documents"}/>
                    </div>
                ) : documents.length === 0 ? (
                    <Text id={"platform-documents-empty"}>No platform documents are available.</Text>
                ) : (
                    <div
                        id={"platform-documents-grid"}
                        className={styles.grid}>
                        {documents.map(entry => (
                            <PlatformDocumentCard
                                key={entry.id}
                                entry={entry}
                                onEdit={() => setEditing(entry)}
                                onUpload={() => setUploading(entry)}
                                onDownload={() => void mutate(() => downloadPlatformDocumentFile(entry))}
                                onPublish={() => void mutate(() => setPlatformDocumentPublished(entry))}
                                onActivate={() => void mutate(() => setPlatformDocumentActive(entry))}
                                onDelete={() => setDeleting(entry)}/>
                        ))}
                    </div>
                )}
            </div>
            <PlatformDocumentDialogs
                editing={editing}
                uploading={uploading}
                deleting={deleting}
                deletePending={deletePending}
                onCloseEditor={() => setEditing(null)}
                onCloseUpload={() => setUploading(null)}
                onCloseDelete={() => setDeleting(null)}
                onSaved={() =>
                {
                    setEditing(null);
                    setUploading(null);
                    void load();
                }}
                onConfirmDelete={() => void confirmDelete()}/>
        </div>
    );
};

export default PlatformDocuments;
