import React, {useEffect, useState} from "react";
import {
    Button,
    Spinner,
    Table,
    TableBody,
    TableCell,
    TableHeader,
    TableHeaderCell,
    TableRow,
    Text
} from "@fluentui/react-components";
import {ArrowDownloadRegular, ArrowUploadRegular} from "@fluentui/react-icons";
import {DocumentDetailedDto, DocumentVersion} from "../../../../models/models";
import {
    downloadDocumentVersion,
    getDocumentVersions,
    uploadDocumentVersion
} from "../../../../../services/sharingSessionApi";
import {useAuth} from "../../../../../context/AuthContext";
import {formatDate} from "../../../../helpers.ts";
import {useSessionDocumentVersionsStyles} from "./SessionDocumentVersionsStyles.tsx";

interface SessionDocumentVersionsProps
{
    sessionId: string;
    sessionDocument: DocumentDetailedDto;
}

const SessionDocumentVersions: React.FC<SessionDocumentVersionsProps> = (
    {
        sessionId,
        sessionDocument
    }) =>
{
    const styles = useSessionDocumentVersionsStyles();
    const {token} = useAuth();
    const [versions, setVersions] = useState<DocumentVersion[]>([]);
    const [loading, setLoading] = useState<boolean>(true);
    const [uploading, setUploading] = useState<boolean>(false);
    const [selectedFile, setSelectedFile] = useState<File | null>(null);
    const [error, setError] = useState<string | null>(null);
    const fileInputRef = React.useRef<HTMLInputElement>(null);

    useEffect(() =>
    {
        fetchVersions();
    }, [sessionId, sessionDocument.id]);

    const fetchVersions = async () =>
    {
        try
        {
            setLoading(true);
            const result = await getDocumentVersions(sessionId, sessionDocument.id);
            setVersions(result as DocumentVersion[]);
            setError(null);
        }
        catch (err: any)
        {
            setError("Failed to load document versions");
            console.error("Error fetching document versions:", err);
        }
        finally
        {
            setLoading(false);
        }
    };

    const handleFileSelect = (event: React.ChangeEvent<HTMLInputElement>) =>
    {
        if (event.target.files && event.target.files.length > 0)
        {
            setSelectedFile(event.target.files[0]);
        }
    };

    const handleUpload = async () =>
    {
        if (!selectedFile)
        {
            return;
        }

        try
        {
            setUploading(true);

            const formData = new FormData();
            formData.append("file", selectedFile);
            formData.append("userEmail", localStorage.getItem("userEmail") || "");

            await uploadDocumentVersion(sessionId, sessionDocument.id, formData, token);

            // Refresh the versions list
            fetchVersions();
            setSelectedFile(null);
            if (fileInputRef.current)
            {
                fileInputRef.current.value = "";
            }
        }
        catch (err: any)
        {
            setError("Failed to upload new version");
            console.error("Error uploading document version:", err);
        }
        finally
        {
            setUploading(false);
        }
    };

    const handleDownload = async (versionId: string) =>
    {
        try
        {
            const blob = await downloadDocumentVersion(sessionId, sessionDocument.id, versionId);

            // Create download link and click it
            const url = window.URL.createObjectURL(blob as Blob);
            const a = document.createElement("a");
            a.style.display = "none";
            a.href = url;
            a.download = `${sessionDocument.title}_${versions.find(v => v.id === versionId)?.version || "document"}`;
            document.body.appendChild(a);
            a.click();

            // Clean up
            window.URL.revokeObjectURL(url);
            document.body.removeChild(a);
        }
        catch (err)
        {
            setError("Failed to download version");
            console.error("Error downloading document version:", err);
        }
    };

    if (loading)
    {
        return <Spinner size={"small"}/>;
    }

    return (
        <div className={styles.container}>
            <div className={styles.versionList}>
                {versions.length === 0 ? (
                    <div className={styles.noVersions}>
                        <Text align="center">No versions available</Text>
                        <Text size={200} align="center">
                            Upload a new version to see it here
                        </Text>
                    </div>
                ) : (
                    <Table>
                        <TableHeader>
                            <TableRow>
                                <TableHeaderCell>#</TableHeaderCell>
                                <TableHeaderCell>Uploaded</TableHeaderCell>
                                <TableHeaderCell>Created By</TableHeaderCell>
                                <TableHeaderCell>Actions</TableHeaderCell>
                            </TableRow>
                        </TableHeader>
                        <TableBody>
                            {versions.map((version) => (
                                <TableRow key={version.id}>
                                    <TableCell>{version.version}</TableCell>
                                    <TableCell>{formatDate(version.createdAt)}</TableCell>
                                    <TableCell>{version.createdByEmail || "Unknown"}</TableCell>
                                    <TableCell>
                                        <Button
                                            icon={<ArrowDownloadRegular/>}
                                            appearance="subtle"
                                            title="Download version"
                                            onClick={() => handleDownload(version.id)}
                                        />
                                    </TableCell>
                                </TableRow>
                            ))}
                        </TableBody>
                    </Table>
                )}
            </div>

            <div className={styles.uploadContainer}>
                <Text weight="semibold">Upload a new version</Text>

                <input
                    type="file"
                    ref={fileInputRef}
                    className={styles.uploadInput}
                    onChange={handleFileSelect}
                    id="version-file-input"
                />

                <div className={styles.buttonContainer}>
                    <Text className={styles.fileLabel}>
                        {selectedFile ? selectedFile.name : "No file selected"}
                    </Text>

                    <div>
                        <Button
                            appearance="secondary"
                            shape={"circular"}
                            size={"small"}
                            onClick={() => fileInputRef.current?.click()}
                            disabled={uploading}
                        >
                            Select File
                        </Button>
                        {" "}
                        <Button
                            appearance="primary"
                            shape={"circular"}
                            size={"small"}
                            onClick={handleUpload}
                            disabled={!selectedFile || uploading}
                            icon={<ArrowUploadRegular/>}
                        >
                            {uploading ? "Uploading..." : "Upload Version"}
                        </Button>
                    </div>
                </div>

                {error && <Text color="red">{error}</Text>}
            </div>
        </div>
    );
};

export default SessionDocumentVersions;