import {
    SelectTabData,
    SelectTabEvent,
    Tab,
    TabList,
    TabValue,
} from "@fluentui/react-components";
import {
    AuditIcon,
    CheckmarkIcon,
    DownloadIcon,
} from "../../components/IconBundles.tsx";
import {auditWorkspaceTabIds} from "../auditWorkspaceTabs.ts";

interface AuditWorkspaceNavigationProps
{
    idPrefix: string;
    selectedValue: TabValue;
    vertical?: boolean;
    onTabSelect: (event: SelectTabEvent, data: SelectTabData) => void;
}

const AuditWorkspaceNavigation = ({
    idPrefix,
    selectedValue,
    vertical = false,
    onTabSelect,
}: AuditWorkspaceNavigationProps) => (
    <TabList
        id={`${idPrefix}-tabs`}
        selectedValue={selectedValue}
        appearance={vertical ? "subtle-circular" : "transparent"}
        vertical={vertical}
        onTabSelect={onTabSelect}>
        <Tab
            id={`${idPrefix}-events-tab`}
            icon={<AuditIcon/>}
            value={auditWorkspaceTabIds.events}>
            Events
        </Tab>
        <Tab
            id={`${idPrefix}-integrity-tab`}
            icon={<CheckmarkIcon/>}
            value={auditWorkspaceTabIds.integrity}>
            Integrity
        </Tab>
        <Tab
            id={`${idPrefix}-exports-tab`}
            icon={<DownloadIcon/>}
            value={auditWorkspaceTabIds.exports}>
            Exports
        </Tab>
    </TabList>
);

export default AuditWorkspaceNavigation;
