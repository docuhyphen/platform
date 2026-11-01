import {Button, Text} from "@fluentui/react-components";
import {UploadIcon} from "../../../../components/IconBundles.tsx";
import {usePreviewEmptyStateStyles} from "./PreviewEmptyStateStyles.tsx";

interface PreviewEmptyStateProps
{
    documentTitle: string;
    isArchivedExchange: boolean;
    canUploadDocument: boolean;
    onUploadDocument?: () => void;
}

const PreviewEmptyState = (
    {
        documentTitle,
        isArchivedExchange,
        canUploadDocument,
        onUploadDocument,
    }: PreviewEmptyStateProps) =>
{
    const styles = usePreviewEmptyStateStyles();

    if (isArchivedExchange)
    {
        return (
            <div id={"exchange-document-previewer-div-2"}
                 className={styles.previewEmptyState}>
                <Text id={"exchange-document-previewer-text-4"}
                      size={500}
                      weight={"semibold"}>Document not yet uploaded</Text>
                <Text id={"exchange-document-previewer-text-5"}
                      size={300}
                      className={styles.previewEmptySubText}
                      align={"center"}>
                    <b id={"exchange-document-previewer-b-1"}>{documentTitle}</b> has no uploaded file, and this Exchange is archived so no further uploads can be made.
                </Text>
            </div>
        );
    }

    return (
        <div className={styles.previewEmptyState}
             id={'preview-empty-state'}>
            <Text id={"exchange-document-previewer-text-6"}
                  size={500}
                  weight={"semibold"}>No file uploaded yet</Text>
            <Text id={"exchange-document-previewer-text-7"}
                  size={300}
                  className={styles.previewEmptySubText}
                  align={"center"}>
                Upload a file for <b id={"exchange-document-previewer-b-2"}>{documentTitle}</b> to start previewing.
            </Text>
            {canUploadDocument && (
                <Button id="exchange-document-preview-upload-empty-cta"
                        appearance="primary"
                        shape="circular"
                        icon={<UploadIcon/>}
                        onClick={onUploadDocument}>
                    Upload document
                </Button>
            )}
            {!canUploadDocument && (
                <Text id={"exchange-document-previewer-text-8"}
                      size={200}
                      className={styles.previewEmptySubText}>
                    You do not have permission to upload this document.
                </Text>
            )}
        </div>
    );
};

export default PreviewEmptyState;
