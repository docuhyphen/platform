import {useEffect, useRef, useState} from 'react';
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
import {AppUserRole, SystemVariableDto} from '../../models/models';
import {getAvailableVariables} from '../../../services/variableService';
import OrganizationVariablesTab, {OrganizationVariablesTabHandle} from '../organization-variables-tab/OrganizationVariablesTab';
import PersonalVariablesTab, {PersonalVariablesTabHandle} from '../personal-variables-tab/PersonalVariablesTab';
import {useAuth} from '../../../context/AuthContext';

type ActiveTab = 'PERSONAL' | 'ORG' | 'PLATFORM';

const tabLabels: Record<ActiveTab, string> = {
    PERSONAL: 'My Variables',
    ORG: 'Organization',
    PLATFORM: 'Platform',
};

const SYSTEM_VARIABLE_COLUMNS = ['Token', 'Description', 'Example'];

const PlatformVariablesView = () =>
{
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
        <div style={{display: 'flex', flexDirection: 'column', gap: '16px', padding: '0 4px'}}>
            <div>
                <Text size={500} weight="semibold" block style={{marginBottom: '4px'}}>System Variables</Text>
                <Text size={300} style={{color: 'var(--colorNeutralForeground3)', display: 'block', marginBottom: '12px'}}>
                    Built-in tokens resolved automatically at exchange creation time. Read-only, no configuration needed.
                </Text>
            </div>
            {loading && <Spinner size="small"/>}
            {!loading && error && <Text style={{color: 'var(--colorPaletteRedForeground1)'}}>{error}</Text>}
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
                                    <code style={{fontFamily: 'monospace', fontSize: '12px'}}>{`{{${sv.token}}}`}</code>
                                </TableCell>
                                <TableCell><Text size={200}>{sv.description}</Text></TableCell>
                                <TableCell>
                                    <Badge appearance="tint" color="brand" size="small">{sv.example}</Badge>
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
    const {appUser, appUserPersonOrganization} = useAuth();
    const roleValue = `${appUser?.role ?? ''}`;
    const canManageOrg =
        appUserPersonOrganization?.isActive &&
        (roleValue === AppUserRole.ORG_ADMIN || roleValue === 'APP_ADMIN');

    const [activeTab, setActiveTab] = useState<ActiveTab>('PERSONAL');
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
        <div style={{display: 'flex', flexDirection: 'column', gap: '16px', width: '100%'}}>
            <div style={{display: 'flex', alignItems: 'center', justifyContent: 'space-between'}}>
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
                        icon={<AddRegular/>}
                        appearance="secondary"
                        shape="circular"
                        onClick={handleAdd}
                    >
                        Add Variable
                    </Button>
                )}
            </div>

            {activeTab === 'PERSONAL' && <PersonalVariablesTab ref={personalRef}/>}
            {activeTab === 'ORG' && <OrganizationVariablesTab ref={orgRef}/>}
            {activeTab === 'PLATFORM' && <PlatformVariablesView/>}
        </div>
    );
};

export default VariablesTab;
