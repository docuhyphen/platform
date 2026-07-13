import {
    Button,
    ProgressBar,
    Text,
} from "@fluentui/react-components";
import {
    bundleIcon,
    FolderZipFilled,
    FolderZipRegular,
    Search16Regular,
} from "@fluentui/react-icons";
import type {IndustryExperience} from "../featureContent.ts";
import {IndustryDocumentCards} from "./IndustryDocumentCards.tsx";
import {useIndustryDocumentPreviewStyles} from "./IndustryDocumentPreviewStyles.tsx";
import {IndustryDocumentToolbar} from "./IndustryDocumentToolbar.tsx";
import {IndustryExchangeDetailHeader} from "./IndustryExchangeDetailHeader.tsx";
import {IndustryExchangeTabsHeader} from "./IndustryExchangeTabsHeader.tsx";
import {useIndustryDocumentViewStyles} from "./IndustryDocumentViewStyles.tsx";

const ZipDocumentsIcon = bundleIcon(FolderZipFilled, FolderZipRegular);

interface IndustryDocumentViewProps
{
    industry: IndustryExperience;
}

export function IndustryDocumentView({industry}: IndustryDocumentViewProps)
{
    const styles = useIndustryDocumentViewStyles();
    const previewStyles = useIndustryDocumentPreviewStyles();
    const documentCount = industry.documentLabels.length;
    const progress = documentCount === 0 ? 0 : 1 / documentCount;

    return (
        <section
            id="industry-demo-exchange-detail"
            className={styles.detail}
            aria-label={`${industry.exchanges[0].title} document preview`}
        >
            <IndustryExchangeDetailHeader title={industry.exchanges[0].title}/>
            <IndustryExchangeTabsHeader/>

            <div
                id="industry-demo-document-search-row"
                className={styles.searchRow}
            >
                <div
                    id="industry-demo-document-search"
                    className={styles.search}
                >
                    <Search16Regular
                        id="industry-demo-document-search-icon"
                        aria-hidden="true"
                    />
                    <span id="industry-demo-document-search-label">
                        Search documents
                    </span>
                </div>
                <div
                    id="industry-demo-document-search-actions"
                    className={styles.searchActions}
                >
                    <Button
                        id="industry-demo-download-documents-zip"
                        appearance="subtle"
                        shape="circular"
                        size="small"
                        aria-label="Download all uploaded documents"
                        icon={<ZipDocumentsIcon id="industry-demo-download-documents-zip-icon"/>}
                    />
                    <div
                        id="industry-demo-document-progress"
                        className={styles.progressSummary}
                        aria-label={`1 of ${documentCount} documents uploaded`}
                    >
                        <Text
                            id="industry-demo-document-progress-text"
                            className={styles.progressText}
                            size={200}
                        >
                            1 of {documentCount} uploaded
                        </Text>
                        <ProgressBar
                            id="industry-demo-document-progress-bar"
                            className={styles.progressBar}
                            value={progress}
                            thickness="medium"
                        />
                    </div>
                </div>
            </div>

            <IndustryDocumentCards labels={industry.documentLabels}/>

            <IndustryDocumentToolbar/>
            <div
                id="industry-demo-document-canvas"
                className={previewStyles.documentCanvas}
            >
                <a
                    id="industry-demo-document-preview-link"
                    className={previewStyles.documentPreviewLink}
                    href={industry.document.pdfSrc}
                    target="_blank"
                    rel="noopener noreferrer"
                    aria-label={`Open ${industry.document.title} PDF`}
                >
                    <img
                        id="industry-demo-document-preview"
                        className={previewStyles.documentPreview}
                        src={industry.document.previewSrc}
                        alt={`${industry.document.title} preview`}
                        loading="lazy"
                    />
                </a>
            </div>
        </section>
    );
}
