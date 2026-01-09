import React, {useState} from "react";
import {useNoAuthSessionDocumentListStyles} from "./NoAuthSessionDocumentListStyles";
import {DocumentBasicDto, NoAuthSharingSessionBasicDto} from "../../../models/models";
import {Button, Card, CardHeader, ProgressBar, Spinner, Text} from "@fluentui/react-components";
import {DocumentAddIcon, DownloadIcon, UploadIcon} from "../../../components/IconBundles";
import {formatDateTimeWithOrdinal} from "../../../helpers";
import {
    downloadNoAuthSharingSessionDocument,
    uploadNoAuthSharingSessionDocument,
} from "../../../../services/sharingSessionApi";

interface NoAuthSessionDocumentListProps
{
    session: NoAuthSharingSessionBasicDto;
}

const NoAuthSessionDocumentList: React.FC<NoAuthSessionDocumentListProps> = ({session}) =>
{
    const styles = useNoAuthSessionDocumentListStyles();
    const [uploading, setUploading] = useState<Record<string, boolean>>({});
    const [progress, setProgress] = useState<Record<string, number>>({});
    const [selectedFiles, setSelectedFiles] = useState<Record<string, File | null>>({});
    const [downloadingDocument, setDownloadingDocument] = useState<boolean>(false);

    const handleFileSelectChange = (e: React.ChangeEvent<HTMLInputElement>, docId: string) =>
    {
        const file = e.target.files ? e.target.files[0] : null;
        if (file)
        {
            setSelectedFiles((prev) => ({...prev, [docId]: file}));
        }
    };

    const handleUploadDocument = async (docId: string, file: File) =>
    {
        setUploading((prev) => ({...prev, [docId]: true}));
        const fileName = file.name;
        const fileExtension = fileName.substring(fileName.lastIndexOf(".")) || "";

        const formData = new FormData();
        formData.append("file", file, fileName);
        formData.append("encryptionMode", "INTERNAL");
        formData.append("extension", fileExtension);

        try
        {
            await uploadNoAuthSharingSessionDocument(session.id, docId, formData, (event: ProgressEvent) =>
            {
                const percentCompleted = Math.round((event.loaded * 100) / event.total);
                setProgress((prev) => ({...prev, [docId]: percentCompleted}));
            });
        }
        catch (error)
        {
            alert("Error uploading document");
            console.error(error);
        }
        finally
        {
            setUploading((prev) => ({...prev, [docId]: false}));
        }
    };

    const handleDocumentUpload = (docId: string) =>
    {
        const file = selectedFiles[docId];
        if (file)
        {
            handleUploadDocument(docId, file);
        }
        else
        {
            alert("Please select a file to upload");
        }
    };

    const handleDownload = async (doc: DocumentBasicDto) =>
    {
        setDownloadingDocument(true);
        try
        {
            const data = await downloadNoAuthSharingSessionDocument(session.id, doc.id);
            const blob = new Blob([data], {type: "application/octet-stream"});
            const url = window.URL.createObjectURL(blob);
            const downloadLink = document.createElement("a");
            downloadLink.href = url;
            downloadLink.setAttribute("download", `${doc.title}.pdf`); // TODO: determine correct file type if needed
            document.body.appendChild(downloadLink);
            downloadLink.click();
            downloadLink.remove();
        }
        catch (error)
        {
            alert("Download failed");
            console.error("Error downloading document:", error);
        }
        finally
        {
            setDownloadingDocument(false);
        }
    };

    const renderDocumentCard = (doc: DocumentBasicDto) =>
    {
        const selectedFile = selectedFiles[doc.id];
        const fileName = selectedFile ? selectedFile.name : "";
        return (
            <Card key={doc.id} className={styles.documentCard}>
                <CardHeader
                    className={styles.documentCardHeader}
                    header={
                        <div className={styles.documentName}>
                            <Text size={500}>{doc.title}</Text>
                            {doc.uploadDate && <Text>Uploaded {formatDateTimeWithOrdinal(doc.uploadDate)}</Text>}
                        </div>
                    }
                />
                <div className={styles.documentActions}>
                    <div className={styles.documentActionsLine1}>
                        <div>
                            <Button
                                appearance="subtle"
                                icon={<DocumentAddIcon/>}
                                shape="circular"
                                disabled={uploading[doc.id]}
                                className={styles.uploadButton1}
                            >
                                {fileName ? "Choose another file" : "Choose file"}
                                <input
                                    type="file"
                                    onChange={(e) => handleFileSelectChange(e, doc.id)}
                                    className={styles.uploadButton2}
                                    disabled={uploading[doc.id]}
                                />
                            </Button>
                            <Button
                                appearance="subtle"
                                icon={<UploadIcon/>}
                                shape="circular"
                                onClick={() => handleDocumentUpload(doc.id)}
                                disabled={uploading[doc.id]}
                            >
                                {uploading[doc.id] ? <Spinner size="tiny"/> : "Upload new document"}
                            </Button>
                        </div>
                        {doc.uploadDate && (
                            <Button
                                appearance="subtle"
                                disabled={downloadingDocument}
                                shape="circular"
                                icon={<DownloadIcon/>}
                                onClick={() => handleDownload(doc)}
                            >
                                Download
                            </Button>
                        )}
                    </div>
                    <div className={styles.documentActionsLine2}>
                        {fileName && (
                            <Text size={300} weight="semibold">
                                Chosen file: {fileName}
                            </Text>
                        )}
                    </div>
                </div>
                {uploading[doc.id] && <ProgressBar value={(progress[doc.id] || 0) / 100}/>}
            </Card>
        );
    };

    return <section className={styles.container}>{session?.documents?.map(renderDocumentCard)}</section>;
};

export default NoAuthSessionDocumentList;
