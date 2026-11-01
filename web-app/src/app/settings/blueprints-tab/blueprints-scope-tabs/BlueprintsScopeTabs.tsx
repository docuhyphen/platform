import {Button, Tab, TabList} from '@fluentui/react-components';
import {AddIcon} from '../../../components/IconBundles.tsx';
import {useGlobalStyles} from '../../../../GlobalStyles.tsx';
import {BlueprintListTab} from '../blueprintListTypes.ts';
import {blueprintTabLabels} from '../blueprintListFilters.ts';
import {useBlueprintsScopeTabsStyles} from './BlueprintsScopeTabsStyles.tsx';

interface BlueprintsScopeTabsProps
{
    activeTab: BlueprintListTab;
    hasOrg: boolean;
    canCreate: boolean;
    onTabSelect: (tab: BlueprintListTab) => void;
    onCreate: () => void;
}

const BlueprintsScopeTabs = ({activeTab, hasOrg, canCreate, onTabSelect, onCreate}: BlueprintsScopeTabsProps) =>
{
    const globalStyles = useGlobalStyles();
    const styles = useBlueprintsScopeTabsStyles();

    return (
        <div id={"blueprints-tab-div-3"}
             className={styles.headerRow}>
            <TabList
                id={"blueprints-tab-tab-list-1"}
                selectedValue={activeTab}
                onTabSelect={(_, d) => onTabSelect(d.value as BlueprintListTab)}
            >
                <Tab id={"blueprints-tab-tab-1"}
                     value="PERSONAL">{blueprintTabLabels.PERSONAL}</Tab>
                {hasOrg && <Tab id={"blueprints-tab-tab-2"}
                                value="ORG">{blueprintTabLabels.ORG}</Tab>}
                <Tab id={"blueprints-tab-tab-3"}
                     value="APP">{blueprintTabLabels.APP}</Tab>
            </TabList>

            {canCreate && (
                <Button
                    id={"button-create-blueprint"}
                    className={globalStyles.buttonWithLoading}
                    icon={<AddIcon/>}
                    appearance="subtle"
                    shape={"circular"}
                    onClick={onCreate}
                >
                    Create Blueprint
                </Button>
            )}
        </div>
    );
};

export default BlueprintsScopeTabs;
