import React from "react";
import {Button, ProgressBar, Tab, TabList, TabValue, Text, Tooltip} from "@fluentui/react-components";
import {AuditIcon, DetailsIcon, DocumentsIcon, ExchangeWorkflowsTabIcon, ExchangeFieldsTabIcon, ZipDocumentsIcon} from "../../../components/IconBundles.tsx";
import {DocumentDetailedDto} from "../../../models/models.tsx";
import {useExchangeTabsHeaderStyles} from "./ExchangeTabsHeaderStyles.tsx";

interface ExchangeTabsHeaderProps {
    activeTab: TabValue;
    documents: DocumentDetailedDto[];
    canDownloadZip: boolean;
    onTabChange: (value: TabValue) => void;
    onDownloadZip: () => void;
}

const ExchangeTabsHeader: React.FC<ExchangeTabsHeaderProps> = (props) => {
    const styles = useExchangeTabsHeaderStyles();
    const uploadedCount = props.documents.filter(document => !!document.uploadDate).length;
    const totalCount = props.documents.length;
    const progress = totalCount === 0 ? 0 : uploadedCount / totalCount;

    return (
        <div id="exchange-tabs-header"
             className={styles.container}>
            <div id="exchange-tabs-viewport"
                 className={styles.tabsViewport}>
                <TabList id="exchange-details-tabs"
                         selectedValue={props.activeTab}
                         onTabSelect={(_, data) => props.onTabChange(data.value)}
                         size="small">
                    <Tab id="exchange-documents-tab"
                         value="documents"
                         icon={<DocumentsIcon/>}>
                        Documents
                    </Tab>
                    <Tab id="exchange-details-tab"
                         value="details"
                         icon={<DetailsIcon/>}>
                        Details
                    </Tab>
                    <Tab id="exchange-audit-tab"
                         value="audit"
                         icon={<AuditIcon/>}>
                        Audit
                    </Tab>
                    <Tab id="exchange-workflow-tab"
                         value="workflow"
                         icon={<ExchangeWorkflowsTabIcon/>}>
                        Workflow
                    </Tab>
                    <Tab id="exchange-fields-tab"
                         value="fields"
                         icon={<ExchangeFieldsTabIcon/>}>
                        Fields
                    </Tab>
                </TabList>
            </div>
            <div id="exchange-tabs-actions"
                 className={styles.headerActions}>
                <Tooltip content="Download all uploaded documents"
                         relationship="description">
                    <Button id="exchange-documents-zip-download"
                            aria-label="Download all uploaded documents"
                            size="small"
                            disabled={!props.canDownloadZip}
                            onClick={props.onDownloadZip}
                            appearance="subtle"
                            shape="circular"
                            icon={<ZipDocumentsIcon/>}/>
                </Tooltip>
                <div id="exchange-document-progress"
                     className={styles.progressSummary}
                     aria-label={`${uploadedCount} of ${totalCount} documents uploaded`}>
                    <Text size={200}
                          className={styles.progressText}>
                        {uploadedCount} of {totalCount} uploaded
                    </Text>
                    <ProgressBar id="exchange-document-progress-bar"
                                 className={styles.progressBar}
                                 value={progress}
                                 thickness="medium"/>
                </div>
            </div>
        </div>
    );
};

export default ExchangeTabsHeader;
