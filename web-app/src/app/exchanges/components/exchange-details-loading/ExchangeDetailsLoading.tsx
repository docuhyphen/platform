import React from "react";
import {Card, SkeletonItem} from "@fluentui/react-components";
import {useExchangeDetailsLoadingStyles} from "./ExchangeDetailsLoadingStyles.tsx";
const SKELETON_DOCUMENT_COUNT = 4;
const ExchangeDetailsLoading: React.FC = () =>
{
    const styles = useExchangeDetailsLoadingStyles();
    return <section id="exchange-details-loading"
                    aria-label="Loading Exchange details"
                    aria-busy="true"
                    className={styles.container}>
        <div id="exchange-details-loading-header"
             className={styles.heading}>
            <div id="exchange-details-loading-header-line"
                 className={styles.headerLine}>
                <SkeletonItem id="exchange-details-loading-name"
                              size={28}
                              className={styles.name}/>
                <div id="exchange-details-loading-actions"
                     className={styles.exchangeActions}>
                    <SkeletonItem id="exchange-details-loading-add-action"
                                  shape="square"
                                  size={32}/>
                    <SkeletonItem id="exchange-details-loading-edit-action"
                                  shape="square"
                                  size={32}/>
                    <SkeletonItem id="exchange-details-loading-access-action"
                                  shape="square"
                                  size={32}/>
                    <SkeletonItem id="exchange-details-loading-more-action"
                                  shape="square"
                                  size={32}
                                  className={styles.compactAction}/>
                    <SkeletonItem id="exchange-details-loading-collapse-action"
                                  shape="square"
                                  size={32}/>
                </div>
            </div>
        </div>
        <div id="exchange-details-loading-tabs-header"
             className={styles.tabsHeader}>
            <div id="exchange-details-loading-tabs"
                 className={styles.tabsViewport}>
                <SkeletonItem id="exchange-details-loading-documents-tab"
                              size={28}
                              className={styles.tabItem}/>
                <SkeletonItem id="exchange-details-loading-details-tab"
                              size={28}
                              className={styles.tabItem}/>
                <SkeletonItem id="exchange-details-loading-workflow-tab"
                              size={28}
                              className={styles.tabItem}/>
            </div>
            <div id="exchange-details-loading-tab-actions"
                 className={styles.tabActions}>
                <SkeletonItem id="exchange-details-loading-zip-action"
                              shape="square"
                              size={32}/>
                <div id="exchange-details-loading-progress-summary"
                     className={styles.progressSummary}>
                    <SkeletonItem id="exchange-details-loading-progress-label"
                                  size={16}
                                  className={styles.progressLabel}/>
                    <SkeletonItem id="exchange-details-loading-progress-bar"
                                  size={8}
                                  className={styles.progressBar}/>
                </div>
                <SkeletonItem id="exchange-details-loading-search-toggle"
                              shape="square"
                              size={32}/>
            </div>
        </div>
        <div id="exchange-details-loading-documents-list"
             className={styles.documentCardListContainer}>
            <div id="exchange-details-loading-strip-layout"
                 className={styles.stripLayout}>
                <div id="exchange-details-loading-cards"
                     className={styles.documentCardList}>
                    {Array.from({length: SKELETON_DOCUMENT_COUNT}).map((_, index) => (
                        <Card id={`exchange-details-loading-document-card-${index + 1}`}
                              key={index}
                              className={styles.documentCard}>
                            <SkeletonItem id={`exchange-details-loading-document-title-${index + 1}`}
                                          size={20}
                                          className={styles.documentTitle}/>
                            <div id={`exchange-details-loading-document-footer-${index + 1}`}
                                 className={styles.documentFooter}>
                                <div id={`exchange-details-loading-document-status-${index + 1}`}
                                     className={styles.documentStatus}>
                                    <SkeletonItem id={`exchange-details-loading-document-status-icon-${index + 1}`}
                                                  shape="circle"
                                                  size={16}/>
                                    <SkeletonItem id={`exchange-details-loading-document-status-text-${index + 1}`}
                                                  size={16}
                                                  className={styles.documentStatusText}/>
                                </div>
                                <div id={`exchange-details-loading-document-actions-${index + 1}`}
                                     className={styles.documentActions}>
                                    <SkeletonItem id={`exchange-details-loading-document-upload-${index + 1}`}
                                                  size={28}
                                                  className={styles.documentUploadAction}/>
                                    <SkeletonItem id={`exchange-details-loading-document-more-${index + 1}`}
                                                  shape="square"
                                                  size={28}
                                                  className={styles.compactAction}/>
                                </div>
                            </div>
                        </Card>
                    ))}
                </div>
            </div>
        </div>
        <div id="exchange-details-loading-preview"
             className={styles.pdfPreviewSection}>
            <div id="exchange-details-loading-preview-page"
                 className={styles.previewPage}>
                <SkeletonItem id="exchange-details-loading-preview-line-1"
                              size={16}
                              className={styles.previewLineWide}/>
                <SkeletonItem id="exchange-details-loading-preview-line-2"
                              size={16}
                              className={styles.previewLineMedium}/>
                <div id="exchange-details-loading-preview-block"
                     className={styles.previewBlock}>
                    {Array.from({length: 6}).map((_, index) => (
                        <SkeletonItem id={`exchange-details-loading-preview-row-${index + 1}`}
                                      key={index}
                                      size={12}
                                      className={styles.previewLine}/>
                    ))}
                </div>
                <div id="exchange-details-loading-preview-block-secondary"
                     className={styles.previewBlock}>
                    {Array.from({length: 4}).map((_, index) => (
                        <SkeletonItem id={`exchange-details-loading-preview-secondary-row-${index + 1}`}
                                      key={index}
                                      size={12}
                                      className={styles.previewLine}/>
                    ))}
                </div>
            </div>
        </div>
    </section>
};
export default ExchangeDetailsLoading;
