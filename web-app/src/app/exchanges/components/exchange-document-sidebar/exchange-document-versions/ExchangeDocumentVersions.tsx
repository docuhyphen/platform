import React, {useCallback, useEffect, useState} from "react";
import {
    Badge,
    Button,
    Dialog,
    DialogBody,
    DialogContent,
    DialogSurface,
    Menu,
    MenuItem,
    MenuList,
    MenuPopover,
    MenuTrigger,
    Spinner,
    Text,
} from "@fluentui/react-components";
import {ArrowDownloadRegular, EyeRegular, ArrowUploadRegular, MoreVerticalRegular, DismissRegular} from "@fluentui/react-icons";
import {DocumentDetailedDto, DocumentVersion, ExchangeDetailedDto} from "../../../../models/models";
import {
    downloadDocumentVersion,
    getDocumentVersions,
} from "../../../../../services/exchangeApi";
import {formatDate} from "../../../../helpers.ts";
import {useExchangeDocumentVersionsStyles} from "./ExchangeDocumentVersionsStyles.tsx";
import ExchangeDocumentUploadDialog
    from "../../exchange-document-upload-dialog/ExchangeDocumentUploadDialog.tsx";
import ExchangeDocumentPreviewer
    from "../../exchange-document-preview/ExchangeDocumentPreviewer.tsx";

interface ExchangeDocumentVersionsProps
{
    exchangeId: string;
    exchangeDocument: DocumentDetailedDto;
    exchange: ExchangeDetailedDto;
    canUpload: boolean;
    canDownload: boolean;
}

const ExchangeDocumentVersions: React.FC<ExchangeDocumentVersionsProps> = (
    {
        exchangeId,
        exchangeDocument,
        exchange,
        canUpload,
        canDownload,
    }) =>
{
    const styles = useExchangeDocumentVersionsStyles();
    const [versions, setVersions] = useState<DocumentVersion[]>([]);
    const [loading, setLoading] = useState<boolean>(true);
    const [error, setError] = useState<string | null>(null);
    const [isUploadDialogOpen, setIsUploadDialogOpen] = useState(false);
    const [actionInProgress, setActionInProgress] = useState<string | null>(null);
    const [previewUrl, setPreviewUrl] = useState<string | null>(null);
    const [previewTitle, setPreviewTitle] = useState<string>("");

    const fetchVersions = useCallback(async () =>
    {
        try
        {
            setLoading(true);
            const result = await getDocumentVersions(exchangeId, exchangeDocument.id);
            setVersions(result as DocumentVersion[]);
            setError(null);
        }
        catch (err: unknown)
        {
            setError("Failed to load document versions");
            console.error("Error fetching document versions:", err);
        }
        finally
        {
            setLoading(false);
        }
    }, [exchangeId, exchangeDocument.id, exchangeDocument.uploadDate]);

    useEffect(() =>
    {
        fetchVersions();
    }, [fetchVersions]);

    const closePreview = () =>
    {
        if (previewUrl)
        {
            window.URL.revokeObjectURL(previewUrl);
        }
        setPreviewUrl(null);
        setPreviewTitle("");
    };

    const openVersionBlob = async (versionId: string, forDownload: boolean) =>
    {
        setActionInProgress(versionId);
        try
        {
            const blob = await downloadDocumentVersion(exchangeId, exchangeDocument.id, versionId);
            const url = window.URL.createObjectURL(blob as Blob);
            if (forDownload)
            {
                const versionLabel = versions.find(v => v.id === versionId)?.version || "document";
                const a = document.createElement("a");
                a.style.display = "none";
                a.href = url;
                a.download = `${exchangeDocument.title}_v${versionLabel}`;
                document.body.appendChild(a);
                a.click();
                document.body.removeChild(a);
                window.URL.revokeObjectURL(url);
            }
            else
            {
                const versionLabel = versions.find(v => v.id === versionId)?.version || "";
                setPreviewTitle(`${exchangeDocument.title} - v${versionLabel}`);
                setPreviewUrl(url);
            }
        }
        catch (err)
        {
            setError(forDownload ? "Failed to download version" : "Failed to preview version");
            console.error("Error opening document version:", err);
        }
        finally
        {
            setActionInProgress(null);
        }
    };

    if (loading)
    {
        return <Spinner size="small"/>;
    }

    return (
        <div className={styles.container}>
            {error && (
                <Text className={styles.errorText}>{error}</Text>
            )}

            <div className={styles.versionList}>
                {versions.length === 0 ? (
                    <div className={styles.noVersions}>
                        <Text align="center">No versions available</Text>
                        <Text size={200} align="center">
                            Upload a version to see it here
                        </Text>
                    </div>
                ) : (
                    versions.map((version) => (
                        <div key={version.id} className={styles.versionCard}>
                            <div className={styles.versionCardHeader}>
                                <Badge appearance="filled" color="informative">
                                    v{version.version}
                                </Badge>
                                {canDownload && (
                                <Menu>
                                    <MenuTrigger disableButtonEnhancement>
                                        <Button
                                            id={`version-actions-trigger-${version.id}`}
                                            icon={actionInProgress === version.id
                                                ? <Spinner size="tiny"/>
                                                : <MoreVerticalRegular/>}
                                            appearance="subtle"
                                            shape={"circular"}
                                            size="small"
                                            disabled={actionInProgress === version.id}
                                        />
                                    </MenuTrigger>
                                    <MenuPopover>
                                        <MenuList>
                                            <MenuItem
                                                icon={<EyeRegular/>}
                                                onClick={() => openVersionBlob(version.id, false)}
                                            >
                                                Preview
                                            </MenuItem>
                                            <MenuItem
                                                icon={<ArrowDownloadRegular/>}
                                                onClick={() => openVersionBlob(version.id, true)}
                                            >
                                                Download
                                            </MenuItem>
                                        </MenuList>
                                    </MenuPopover>
                                </Menu>
                                )}
                            </div>
                            <div className={styles.versionCardMeta}>
                                <Text size={200}>{formatDate(version.createdAt)}</Text>
                                <Text size={200}>{version.createdByEmail || "Unknown"}</Text>
                            </div>
                            {version.fileName && (
                                <Text size={200} className={styles.versionFileName}>
                                    {version.fileName}
                                </Text>
                            )}
                        </div>
                    ))
                )}
            </div>

            {canUpload && (
                <div className={styles.uploadButtonRow}>
                    <Button
                        id={"exchange-document-version-upload-btn"}
                        appearance="primary"
                        shape="circular"
                        size="small"
                        icon={<ArrowUploadRegular/>}
                        onClick={() => setIsUploadDialogOpen(true)}
                    >
                        Upload Version
                    </Button>
                </div>
            )}

            <ExchangeDocumentUploadDialog
                isOpen={isUploadDialogOpen}
                exchangeId={exchangeId}
                exchangeDocument={exchangeDocument}
                onDismiss={() => setIsUploadDialogOpen(false)}
                onDocumentUploaded={() =>
                {
                    setIsUploadDialogOpen(false);
                    fetchVersions();
                }}
            />

            {/* Version preview dialog using ExchangeDocumentPreviewer */}
            <Dialog
                open={!!previewUrl}
                onOpenChange={(_e, data) => { if (!data.open) closePreview(); }}
            >
                <DialogSurface className={styles.previewDialogSurface}>
                    <Button
                        appearance="subtle"
                        shape="circular"
                        size="small"
                        icon={<DismissRegular/>}
                        className={styles.previewCloseButton}
                        onClick={closePreview}
                    />
                    <DialogBody className={styles.previewDialogBody}>
                        <DialogContent className={styles.previewDialogContent}>
                            {previewUrl && (
                                <ExchangeDocumentPreviewer
                                    document={exchangeDocument}
                                    exchange={exchange}
                                    overridePdfUrl={previewUrl}
                                    hideEnlarge
                                />
                            )}
                        </DialogContent>
                    </DialogBody>
                </DialogSurface>
            </Dialog>
        </div>
    );
};

export default ExchangeDocumentVersions;