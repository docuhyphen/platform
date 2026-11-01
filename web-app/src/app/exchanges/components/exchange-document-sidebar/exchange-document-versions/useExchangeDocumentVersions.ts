import {useCallback, useEffect, useState} from "react";
import {DocumentDetailedDto, DocumentVersion} from "../../../../models/models";
import {downloadDocumentVersion, getDocumentVersions} from "../../../../../services/exchangeApi";
import {triggerBlobDownload} from "./documentVersionDownload.ts";

export interface ExchangeDocumentVersionsState
{
    versions: DocumentVersion[];
    loading: boolean;
    error: string | null;
    actionInProgress: string | null;
    previewUrl: string | null;
    previewVersionId: string | null;
    fetchVersions: () => Promise<void>;
    closePreview: () => void;
    previewVersion: (versionId: string) => Promise<void>;
    downloadVersion: (versionId: string) => Promise<void>;
}

export const useExchangeDocumentVersions = (
    exchangeId: string,
    exchangeDocument: DocumentDetailedDto
): ExchangeDocumentVersionsState =>
{
    const [versions, setVersions] = useState<DocumentVersion[]>([]);
    const [loading, setLoading] = useState<boolean>(true);
    const [error, setError] = useState<string | null>(null);
    const [actionInProgress, setActionInProgress] = useState<string | null>(null);
    const [previewUrl, setPreviewUrl] = useState<string | null>(null);
    const [previewVersionId, setPreviewVersionId] = useState<string | null>(null);

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
    }, [exchangeId, exchangeDocument]);

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
        setPreviewVersionId(null);
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
                triggerBlobDownload(url, `${exchangeDocument.title}_v${versionLabel}`);
            }
            else
            {
                setPreviewVersionId(versionId);
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

    return {
        versions,
        loading,
        error,
        actionInProgress,
        previewUrl,
        previewVersionId,
        fetchVersions,
        closePreview,
        previewVersion: (versionId: string) => openVersionBlob(versionId, false),
        downloadVersion: (versionId: string) => openVersionBlob(versionId, true),
    };
};
