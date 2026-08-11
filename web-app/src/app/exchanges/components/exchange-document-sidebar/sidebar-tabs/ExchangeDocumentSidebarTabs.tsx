import {
    SelectTabData,
    SelectTabEvent,
    Tab,
    TabList,
    TabValue,
} from "@fluentui/react-components";
import {
    AuditIcon,
    CommentIcon,
    DocumentVersionsIcon,
} from "../../../../components/IconBundles.tsx";

interface ExchangeDocumentSidebarTabsProps
{
    selectedValue: TabValue;
    canViewAudit: boolean;
    canViewVersions: boolean;
    onTabSelect: (event: SelectTabEvent, data: SelectTabData) => void;
}

const ExchangeDocumentSidebarTabs = (props: ExchangeDocumentSidebarTabsProps) =>
{
    return (
        <TabList
            id={"exchange-document-sidebar-tabs"}
            selectedValue={props.selectedValue}
            onTabSelect={props.onTabSelect}
        >
            <Tab
                id={"comments"}
                icon={<CommentIcon/>}
                value={"comments"}
            >
                Notes/Comments
            </Tab>
            {props.canViewVersions && (
                <Tab
                    id={"versions"}
                    icon={<DocumentVersionsIcon/>}
                    value={"versions"}
                >
                    Versions
                </Tab>
            )}
            {props.canViewAudit && (
                <Tab
                    id={"audit"}
                    icon={<AuditIcon/>}
                    value={"audit"}
                >
                    Audit
                </Tab>
            )}
        </TabList>
    );
};

export default ExchangeDocumentSidebarTabs;
