import React, {useState} from "react";
import {useNoAuthSessionDocumentListStyles} from "./NoAuthSessionDocumentListStyles.tsx";
import {DocumentBasicDto, NoAuthSharingSessionBasicDto} from "../../../models/models.tsx";
import {Button, Card, CardHeader, ProgressBar, Spinner, Text} from "@fluentui/react-components";
import {DocumentAddIcon, DownloadIcon, UploadIcon} from "../../../components/IconBundles.tsx";
import {formatDateTimeWithOrdinal} from "../../../helpers.ts";
import {
    downloadNoAuthSharingSessionDocument,
    uploadNoAuthSharingSessionDocument
} from "../../../../services/sharingSessionApi.ts";

interface NoAuthSessionDocumentListProps
{
    session: NoAuthSharingSessionBasicDto;
}

const NoAuthSessionDocumentList: React.FC<NoAuthSessionDocumentListProps> = ({session}) =>
{
    const styles = useNoAuthSessionDocumentListStyles();
    const [uploading, setUploading] = useState<{ [key: string]: boolean }>({});
    const [progress, setProgress] = useState<{ [key: string]: number }>({});
    const [selectedFileName, setSelectedFileName] = useState<string>('');
    const [selectedFile, setSelectedFile] = useState<any>();
    const [downloadingDocument, setDownloadingDocument] = useState<boolean>();

    const onUploadDocument = async (sessionDocumentId: string, file: File) =>
    {
        setUploading((prev) => ({...prev, [sessionDocumentId]: true}));
        const fileName = file.name;
        const fileExtension = fileName.substring(fileName.lastIndexOf(".")) || "";

        const formData = new FormData();
        formData.append("file", file, fileName);
        formData.append("encryptionMode", "INTERNAL");
        formData.append("extension", fileExtension);

        try
        {
            await uploadNoAuthSharingSessionDocument(session.id, sessionDocumentId, formData, (event) =>
            {
                console.log("Progress update", event);

                const percentCompleted = Math.round((event.loaded * 100) / event.total);
                setProgress((prev) => ({...prev, [sessionDocumentId]: percentCompleted}));
            });
        }
        catch (error)
        {
            alert("Error uploading document");
            console.error(error);
        }
        finally
        {
            setUploading((prev) => ({...prev, [sessionDocumentId]: false}));
        }
    };

    const onFileSelectChange = (e: React.ChangeEvent<HTMLInputElement>) =>
    {
        if (e.target.files && e.target.files[0])
        {
            setSelectedFileName(e.target.files[0].name);
            setSelectedFile(e.target.files[0]);
        }
    };

    const onDocumentUpload = (documentId: string) =>
    {
        if (selectedFile)
        {
            onUploadDocument(documentId, selectedFile);
        }
        else
        {
            alert("Please select a file to upload");
        }
    }

    const onDownload = async (sessionDocument: DocumentBasicDto) =>
    {
        setDownloadingDocument(true)

        try
        {
            const data = await downloadNoAuthSharingSessionDocument(session.id, sessionDocument.id);
            const url = window.URL.createObjectURL(new Blob([data], {type: 'application/octet-stream'}));
            const link = window.document.createElement('a');

            link.id = 'f-download-link';
            link.href = url;
            link.setAttribute('download', `${sessionDocument.title}.pdf`); //ToDo: get type from document

            window.document.body.appendChild(link);

            link.click();

            window.document.getElementById('f-download-link')?.remove();
        }
        catch (error)
        {
            alert("Download failed");
            console.error("Error deleting document:", error);
        }
        finally
        {
            setDownloadingDocument(false);
        }
    }

    const renderDocumentCard = (sessionDocument: DocumentBasicDto) =>
    {

        return (
            <Card key={sessionDocument.id} className={styles.documentCard}>
                <CardHeader
                    className={styles.documentCardHeader}
                    header={
                        <div className={styles.documentName}>
                            <Text size={500}>
                                {sessionDocument.title}
                            </Text>
                            {sessionDocument.uploadDate && (
                                <Text>
                                    Uploaded {formatDateTimeWithOrdinal(sessionDocument.uploadDate)}
                                </Text>
                            )}
                        </div>
                    }
                />
                <div className={styles.documentActions}>
                    <div className={styles.documentActionsLine1}>
                        <div>
                            <Button appearance="subtle"
                                    icon={<DocumentAddIcon/>}
                                    shape={"circular"}
                                    disabled={uploading[sessionDocument.id]}
                                    className={styles.uploadButton1}>
                                {selectedFileName && "Choose another file"}
                                {!selectedFileName && "Choose file"}
                                <input type="file"
                                       onChange={onFileSelectChange}
                                       className={styles.uploadButton2}
                                       disabled={uploading[sessionDocument.id]}/>
                            </Button>
                            <Button appearance="subtle"
                                    icon={<UploadIcon/>}
                                    shape={"circular"}
                                    onClick={() => onDocumentUpload(sessionDocument.id)}
                                    disabled={uploading[sessionDocument.id]}>
                                {uploading[sessionDocument.id] ? <Spinner size="extra-small"/> : "Upload new document"}
                            </Button>
                        </div>
                        {sessionDocument && sessionDocument.uploadDate &&

                            <Button appearance="subtle"
                                    disabled={downloadingDocument}
                                    shape={"circular"}
                                    icon={<DownloadIcon/>}
                                    onClick={() => onDownload(sessionDocument)}>
                                Download
                            </Button>
                            }
                    </div>
                    <div className={styles.documentActionsLine2}>
                        {selectedFileName &&
                            <Text size={300}
                                  weight={"semibold"}>
                                Chosen file: {selectedFileName}
                            </Text>
                        }
                    </div>
                </div>
                {uploading[sessionDocument.id] &&
                    <ProgressBar value={progress[sessionDocument.id] / 100}/>
                }
            </Card>
        );
    };

    return (
        <section className={styles.container}>
            {session?.documents?.map((document: any) => renderDocumentCard(document))}
        </section>
    );
};

export default NoAuthSessionDocumentList;