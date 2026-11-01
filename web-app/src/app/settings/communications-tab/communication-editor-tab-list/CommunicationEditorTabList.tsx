import {Tab, TabList} from '@fluentui/react-components';
import {CommunicationEditorTab} from '../communicationEditorTypes';
import {useCommunicationEditorTabListStyles} from './CommunicationEditorTabListStyles';

interface Props
{
    activeTab: CommunicationEditorTab;
    previewEnabled: boolean;
    onTabChange: (tab: CommunicationEditorTab) => void;
}

const CommunicationEditorTabList = ({activeTab, previewEnabled, onTabChange}: Props) =>
{
    const styles = useCommunicationEditorTabListStyles();

    return (
        <TabList
            id={"communication-editor-dialog-tab-list-1"}
            selectedValue={activeTab}
            onTabSelect={(_, d) => onTabChange(d.value as CommunicationEditorTab)}
            className={styles.tabList}
        >
            <Tab id={"communication-editor-dialog-tab-1"}
                 value="details">Details</Tab>
            <Tab id={"communication-editor-dialog-tab-2"}
                 value="preview"
                 disabled={!previewEnabled}>Preview</Tab>
        </TabList>
    );
};

export default CommunicationEditorTabList;
