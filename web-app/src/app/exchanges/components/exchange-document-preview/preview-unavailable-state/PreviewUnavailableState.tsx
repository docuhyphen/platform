import {Button, Text} from "@fluentui/react-components";
import {PreviewDownloads} from "../usePreviewDownloads.ts";
import {usePreviewUnavailableStateStyles} from "./PreviewUnavailableStateStyles.tsx";

interface PreviewUnavailableStateProps
{
    previewError: string;
    canDownloadOriginal: boolean;
    canDownloadAsPdf: boolean;
    showDownloadDisabledNotice: boolean;
    downloads: PreviewDownloads;
}

const PreviewUnavailableState = (
    {
        previewError,
        canDownloadOriginal,
        canDownloadAsPdf,
        showDownloadDisabledNotice,
        downloads,
    }: PreviewUnavailableStateProps) =>
{
    const styles = usePreviewUnavailableStateStyles();

    return (
        <div id={"exchange-document-previewer-div-1"}
             className={styles.previewErrorContainer}>
            <Text id={"exchange-document-previewer-text-1"}
                  size={500}
                  weight={"semibold"}>Preview unavailable</Text>
            <Text id={"exchange-document-previewer-text-2"}
                  size={300}>{previewError}</Text>
            {canDownloadOriginal && (
                <Button appearance="primary"
                        id="exchange-document-preview-download-original"
                        shape="circular"
                        disabled={downloads.downloadingOriginal}
                        onClick={downloads.downloadOriginal}>
                    {downloads.downloadingOriginal ? 'Preparing download...' : 'Download original'}
                </Button>
            )}
            {canDownloadAsPdf && (
                <Button appearance="secondary"
                        id="exchange-document-preview-download-pdf"
                        shape="circular"
                        disabled={downloads.downloadingPdf}
                        onClick={downloads.downloadAsPdf}>
                    {downloads.downloadingPdf ? 'Preparing PDF…' : 'Download as PDF'}
                </Button>
            )}
            {showDownloadDisabledNotice && (
                <Text id={"exchange-document-previewer-text-3"}
                      size={200}
                      italic>
                    Download is disabled for this Exchange.
                </Text>
            )}
        </div>
    );
};

export default PreviewUnavailableState;
