import {
    Tab,
    TabList,
} from "@fluentui/react-components";
import {
    BranchRegular,
    bundleIcon,
    DocumentBulletListClockFilled,
    DocumentBulletListClockRegular,
    DocumentBulletListMultipleFilled,
    DocumentBulletListMultipleRegular,
    DocumentOnePageFilled,
    DocumentOnePageRegular,
} from "@fluentui/react-icons";
import {useIndustryExchangeTabsHeaderStyles} from "./IndustryExchangeTabsHeaderStyles.tsx";

const DocumentsIcon = bundleIcon(DocumentBulletListMultipleFilled, DocumentBulletListMultipleRegular);
const DetailsIcon = bundleIcon(DocumentOnePageFilled, DocumentOnePageRegular);
const ExchangeWorkflowsTabIcon = bundleIcon(BranchRegular, BranchRegular);
const AuditIcon = bundleIcon(DocumentBulletListClockFilled, DocumentBulletListClockRegular);

export function IndustryExchangeTabsHeader()
{
    const styles = useIndustryExchangeTabsHeaderStyles();

    return (
        <div
            id="industry-demo-exchange-tabs-header"
            className={styles.container}
        >
            <div
                id="industry-demo-exchange-tabs-viewport"
                className={styles.tabsViewport}
            >
                <TabList
                    id="industry-demo-exchange-tabs"
                    selectedValue="documents"
                    size="small"
                >
                    <Tab
                        id="industry-demo-documents-tab"
                        value="documents"
                        icon={<DocumentsIcon id="industry-demo-documents-tab-icon"/>}
                    >
                        Documents
                    </Tab>
                    <Tab
                        id="industry-demo-details-tab"
                        value="details"
                        icon={<DetailsIcon id="industry-demo-details-tab-icon"/>}
                    >
                        Details
                    </Tab>
                    <Tab
                        id="industry-demo-workflow-tab"
                        value="workflow"
                        icon={<ExchangeWorkflowsTabIcon id="industry-demo-workflow-tab-icon"/>}
                    >
                        Workflow
                    </Tab>
                    <Tab
                        id="industry-demo-audit-tab"
                        value="audit"
                        icon={<AuditIcon id="industry-demo-audit-tab-icon"/>}
                    >
                        Audit
                    </Tab>
                </TabList>
            </div>
        </div>
    );
}
