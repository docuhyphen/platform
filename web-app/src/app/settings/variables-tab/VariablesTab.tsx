import {useEffect, useRef, useState} from 'react';
import ViewModeToggle from '../../components/ViewModeToggle.tsx';
import {updateAppUserSettings} from '../../../services/appUserApi';
import {
    Badge,
    Button,
    Spinner,
    Tab,
    TabList,
    Table,
    TableBody,
    TableCell,
    TableHeader,
    TableHeaderCell,
    TableRow,
    Text,
} from '@fluentui/react-components';
import {AddRegular} from '@fluentui/react-icons';
import {AppUserRole, SystemVariableDto, ViewMode} from '../../models/models';
import {getAvailableVariables} from '../../../services/variableService';
import OrganizationVariablesTab, {OrganizationVariablesTabHandle} from '../organization-variables-tab/OrganizationVariablesTab';
import PersonalVariablesTab, {PersonalVariablesTabHandle} from '../personal-variables-tab/PersonalVariablesTab';
import {useAuth} from '../../../context/AuthContext';
import {useVariablesTabStyles} from './VariablesTabStyles';

type ActiveTab = 'PERSONAL' | 'ORG' | 'PLATFORM';

const tabLabels: Record<ActiveTab, string> = {
    PERSONAL: 'My Variables',
    ORG: 'Organization',
    PLATFORM: 'Platform',
};

const SYSTEM_VARIABLE_COLUMNS = ['Token', 'Description', 'Example'];

const PlatformVariablesView = () =>
{
    const styles = useVariablesTabStyles();
    const [systemVars, setSystemVars] = useState<SystemVariableDto[]>([]);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<string | null>(null);

    useEffect(() =>
    {
        setLoading(true);
        getAvailableVariables()
            .then(available => setSystemVars(available.system))
            .catch(() => setError('Failed to load system variables'))
            .finally(() => setLoading(false));
    }, []);

    return (
        <div className={styles.platformContainer}>
            <div>
                <Text
                    size={500}
                    weight="semibold"
                    block
                    className={styles.systemVarTitle}
                >
                    System Variables
                </Text>
                <Text
                    size={300}
                    className={styles.systemVarSubtitle}
                >
                    Built-in tokens resolved automatically at exchange creation time. Read-only, no configuration needed.
                </Text>
            </div>
            {loading && <Spinner size="small"/>}
            {!loading && error && (
                <Text className={styles.errorText}>{error}</Text>
            )}
            {!loading && !error && (
                <Table size="small">
                    <TableHeader>
                        <TableRow>
                            {SYSTEM_VARIABLE_COLUMNS.map(c => <TableHeaderCell key={c}>{c}</TableHeaderCell>)}
                        </TableRow>
                    </TableHeader>
                    <TableBody>
                        {systemVars.map(sv => (
                            <TableRow key={sv.token}>
                                <TableCell>
                                    <code className={styles.codeCell}>{`{{${sv.token}}}`}</code>
                                </TableCell>
                                <TableCell><Text size={200}>{sv.description}</Text></TableCell>
                                <TableCell>
                                    <Badge
                                        appearance="tint"
                                        color="brand"
                                        size="small"
                                    >
                                        {sv.example}
                                    </Badge>
                                </TableCell>
                            </TableRow>
                        ))}
                    </TableBody>
                </Table>
            )}
        </div>
    );
};

const VariablesTab = () =>
{
    const styles = useVariablesTabStyles();
    const {appUser, setAppUser, token, appUserPersonOrganization} = useAuth();
    const roleValue = `${appUser?.role ?? ''}`;
    const canManageOrg =
        appUserPersonOrganization?.isActive &&
        (roleValue === AppUserRole.ORG_ADMIN || roleValue === 'APP_ADMIN');

    const [activeTab, setActiveTab] = useState<ActiveTab>('PERSONAL');
    const [viewMode, setViewMode] = useState<ViewMode>(appUser?.settings?.variablesView ?? 'cards');
    const personalRef = useRef<PersonalVariablesTabHandle>(null);
    const orgRef = useRef<OrganizationVariablesTabHandle>(null);

    const handleViewModeChange = async (mode: ViewMode) => {
        setViewMode(mode);
        if (!appUser?.settings) return;
        const updated = {...appUser.settings, variablesView: mode};
        try { await updateAppUserSettings(updated, token); if (appUser) setAppUser({...appUser, settings: updated}); }
        catch { /* non-critical */ }
    };

    const handleAdd = () =>
    {
        if (activeTab === 'PERSONAL') personalRef.current?.openCreate();
        else if (activeTab === 'ORG') orgRef.current?.openCreate();
    };

    const canAdd =
        activeTab === 'PERSONAL' ||
        (activeTab === 'ORG' && !!canManageOrg);

    return (
        <div className={styles.container}>
            <div className={styles.header}>
                <TabList
                    selectedValue={activeTab}
                    onTabSelect={(_, d) => setActiveTab(d.value as ActiveTab)}
                >
                    <Tab value="PERSONAL">{tabLabels.PERSONAL}</Tab>
                    <Tab value="ORG">{tabLabels.ORG}</Tab>
                    <Tab value="PLATFORM">{tabLabels.PLATFORM}</Tab>
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

            {activeTab !== 'PLATFORM' && (
                <div className={styles.toolbar}>
                    <ViewModeToggle value={viewMode} onChange={handleViewModeChange}/>
                </div>
            )}

            {activeTab === 'PERSONAL' && <PersonalVariablesTab ref={personalRef} viewMode={viewMode}/>}
            {activeTab === 'ORG' && <OrganizationVariablesTab ref={orgRef} viewMode={viewMode}/>}
            {activeTab === 'PLATFORM' && <PlatformVariablesView/>}
        </div>
    );
};

export default VariablesTab;
