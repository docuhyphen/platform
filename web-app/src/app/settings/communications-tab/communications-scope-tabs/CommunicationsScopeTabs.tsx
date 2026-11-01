import {Button, Tab, TabList} from '@fluentui/react-components';
import {AddIcon} from '../../../components/IconBundles';
import {CommunicationListTab} from '../communicationListTypes';
import {communicationTabLabels} from '../communicationListFilters';
import {useCommunicationsScopeTabsStyles} from './CommunicationsScopeTabsStyles';

interface Props
{
    activeTab: CommunicationListTab;
    hasOrg: boolean;
    canCreate: boolean;
    onTabChange: (tab: CommunicationListTab) => void;
    onCreate: () => void;
}

const CommunicationsScopeTabs = ({activeTab, hasOrg, canCreate, onTabChange, onCreate}: Props) =>
{
    const styles = useCommunicationsScopeTabsStyles();

    return (
        <div id={"communications-tab-div-2"}
             className={styles.headerRow}>
            <TabList
                id={"communications-tabs"}
                selectedValue={activeTab}
                onTabSelect={(_, d) => onTabChange(d.value as CommunicationListTab)}
            >
                <Tab
                    id={"communications-tab-personal"}
                    value="PERSONAL"
                >
                    {communicationTabLabels.PERSONAL}
                </Tab>
                {hasOrg && (
                    <Tab
                        id={"communications-tab-organization"}
                        value="ORG"
                    >
                        {communicationTabLabels.ORG}
                    </Tab>
                )}
                <Tab
                    id={"communications-tab-platform"}
                    value="PLATFORM"
                >
                    {communicationTabLabels.PLATFORM}
                </Tab>
            </TabList>

            {canCreate && (
                <Button
                    id={"button-create-communication"}
                    appearance="subtle"
                    shape={"circular"}
                    icon={<AddIcon/>}
                    onClick={onCreate}
                >
                    Create Communication
                </Button>
            )}
        </div>
    );
};

export default CommunicationsScopeTabs;
