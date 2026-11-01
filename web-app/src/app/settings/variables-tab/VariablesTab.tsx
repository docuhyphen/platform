import {useRef, useState} from 'react';
import ViewModeToggle from '../../components/view-mode-toggle/ViewModeToggle.tsx';
import {Button, Tab, TabList} from '@fluentui/react-components';
import {AddRegular} from '@fluentui/react-icons';
import {Capability} from '../../models/models';
import OrganizationVariablesTab, {OrganizationVariablesTabHandle} from '../organization-variables-tab/OrganizationVariablesTab';
import PersonalVariablesTab, {PersonalVariablesTabHandle} from '../personal-variables-tab/PersonalVariablesTab';
import PlatformVariablesView from './platform-variables-view/PlatformVariablesView.tsx';
import {useAuth} from '../../../context/AuthContext';
import {useViewModePreference} from '../components/useViewModePreference.ts';
import {useVariablesTabStyles} from './VariablesTabStyles';

type ActiveTab = 'PERSONAL' | 'ORG' | 'PLATFORM';

const tabLabels: Record<ActiveTab, string> = {
    PERSONAL: 'My Variables',
    ORG: 'Organization',
    PLATFORM: 'Platform',
};

const VariablesTab = () =>
{
    const styles = useVariablesTabStyles();
    const {appUserPersonOrganization, hasCapability} = useAuth();
    const hasOrg = !!appUserPersonOrganization?.isActive;
    const canManageOrg =
        appUserPersonOrganization?.isActive &&
        hasCapability(Capability.ORG_POLICY_MANAGE);

    const [activeTab, setActiveTab] = useState<ActiveTab>('PERSONAL');
    const {viewMode, changeViewMode} = useViewModePreference('variablesView');
    const personalRef = useRef<PersonalVariablesTabHandle>(null);
    const orgRef = useRef<OrganizationVariablesTabHandle>(null);

    const handleAdd = () =>
    {
        if (activeTab === 'PERSONAL') personalRef.current?.openCreate();
        else if (activeTab === 'ORG') orgRef.current?.openCreate();
    };

    const canAdd =
        activeTab === 'PERSONAL' ||
        (activeTab === 'ORG' && !!canManageOrg);

    return (
        <div id={"variables-tab-div-4"}
             className={styles.container}>
            <div id={"variables-tab-div-5"}
                 className={styles.header}>
                <TabList
                    id={"variables-tab-tab-list-1"}
                    selectedValue={activeTab}
                    onTabSelect={(_, d) => setActiveTab(d.value as ActiveTab)}
                >
                    <Tab id={"variables-tab-tab-1"}
                         value="PERSONAL">{tabLabels.PERSONAL}</Tab>
                    {hasOrg && <Tab id={"variables-tab-tab-2"}
                                    value="ORG">{tabLabels.ORG}</Tab>}
                    <Tab id={"variables-tab-tab-3"}
                         value="PLATFORM">{tabLabels.PLATFORM}</Tab>
                </TabList>

                {canAdd && (
                    <Button
                        id={"button-create-variable"}
                        icon={<AddRegular/>}
                        appearance="subtle"
                        shape={"circular"}
                        onClick={handleAdd}
                    >
                        Create Variable
                    </Button>
                )}
            </div>

            <div id={"variables-tab-div-6"}
                 className={styles.scrollableContent}>
                {activeTab !== 'PLATFORM' && (
                    <div id={"variables-tab-div-7"}
                         className={styles.toolbar}>
                        <ViewModeToggle value={viewMode}
                                        onChange={changeViewMode}/>
                    </div>
                )}

                {activeTab === 'PERSONAL' && <PersonalVariablesTab ref={personalRef}
                                                                   viewMode={viewMode}/>}
                {activeTab === 'ORG' && <OrganizationVariablesTab ref={orgRef}
                                                                  viewMode={viewMode}/>}
                {activeTab === 'PLATFORM' && <PlatformVariablesView/>}
            </div>
        </div>
    );
};

export default VariablesTab;
