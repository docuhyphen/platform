import {Tab, TabList} from '@fluentui/react-components';
import {BlueprintEditorTab} from '../blueprintEditorTypes.ts';
import {useBlueprintEditorTabListStyles} from './BlueprintEditorTabListStyles.tsx';

interface BlueprintEditorTabListProps
{
    activeTab: BlueprintEditorTab;
    onTabSelect: (tab: BlueprintEditorTab) => void;
}

const BlueprintEditorTabList = ({activeTab, onTabSelect}: BlueprintEditorTabListProps) =>
{
    const styles = useBlueprintEditorTabListStyles();

    return (
        <TabList
            id={"blueprint-editor-tabs"}
            selectedValue={activeTab}
            onTabSelect={(_, d) => onTabSelect(d.value as BlueprintEditorTab)}
            className={styles.tabList}
        >
            <Tab id={"blueprint-editor-details-tab"}
                 value="details">
                Details
            </Tab>
            <Tab id={"blueprint-editor-documents-tab"}
                 value="documents">
                Documents
            </Tab>
            <Tab id={"blueprint-editor-fields-tab"}
                 value="fields">
                Business Fields
            </Tab>
            <Tab id={"blueprint-editor-information-request-tab"}
                 value="informationRequest">
                Information Request
            </Tab>
            <Tab id={"blueprint-editor-permissions-tab"}
                 value="permissions">
                Permissions
            </Tab>
        </TabList>
    );
};

export default BlueprintEditorTabList;
