import {useEffect, useMemo, useRef, useState} from 'react';
import {
    Button,
    Checkbox,
    Field,
    Menu,
    MenuItem,
    MenuList,
    MenuPopover,
    MenuTrigger,
    Popover,
    PopoverSurface,
    PopoverTrigger,
    SearchBox,
    Tab,
    TabList,
    TabValue,
    Text,
    Tooltip,
} from '@fluentui/react-components';
import {useAuth} from '../../../context/AuthContext';
import {
    Capability,
    FieldDefinitionDto,
    FieldLifecycleStatus,
    FieldValueType,
    SchemaDefinitionDto,
    ViewMode,
} from '../../models/models';
import {useFieldsTabStyles} from './FieldsTabStyles';
import {AddIcon, CheckmarkIcon, FilterIcon, SortDownIcon, SortUpIcon} from '../../components/IconBundles';
import {listFieldDefinitions, listSchemas, retireFieldDefinition} from '../../../services/fieldsService';
import FieldDefinitionsPanel from './FieldDefinitionsPanel';
import SchemasPanel, {SchemasPanelHandle} from './SchemasPanel';
import FieldDefinitionDialog from './FieldDefinitionDialog';
import ViewModeToggle from '../../components/ViewModeToggle';
import {VALUE_TYPE_LABELS} from './fieldLabels';

type SortOrder = 'default' | 'nameAsc' | 'nameDesc';

const toggleSetItem = <T,>(set: Set<T>, item: T): Set<T> =>
{
    const next = new Set(set);
    if (next.has(item)) next.delete(item); else next.add(item);
    return next;
};

const FieldsTab = () =>
{
    const styles = useFieldsTabStyles();
    const {appUserPersonOrganization, hasCapability} = useAuth();
    const canManage = !!appUserPersonOrganization?.isActive &&
        (hasCapability(Capability.APP_ADMIN) || hasCapability(Capability.ORG_POLICY_MANAGE));

    const [selected, setSelected] = useState<TabValue>('fields');

    // ── Fields state ──────────────────────────────────────────────────────────
    const [definitions, setDefinitions] = useState<FieldDefinitionDto[]>([]);
    const [defsLoading, setDefsLoading] = useState(false);
    const [defsError, setDefsError] = useState<string | null>(null);
    const [dialogOpen, setDialogOpen] = useState(false);
    const [fieldSearch, setFieldSearch] = useState('');
    const [fieldViewMode, setFieldViewMode] = useState<ViewMode>('cards');
    const [fieldFilterNamespaces, setFieldFilterNamespaces] = useState<Set<string>>(new Set());
    const [fieldFilterTypes, setFieldFilterTypes] = useState<Set<FieldValueType>>(new Set());
    const [fieldFilterSearch, setFieldFilterSearch] = useState('');

    // ── Schemas state ─────────────────────────────────────────────────────────
    const [schemas, setSchemas] = useState<SchemaDefinitionDto[]>([]);
    const [schemasLoading, setSchemasLoading] = useState(false);
    const [schemasError, setSchemasError] = useState<string | null>(null);
    const [schemaSearch, setSchemaSearch] = useState('');
    const [schemaViewMode, setSchemaViewMode] = useState<ViewMode>('cards');
    const [schemaFilterNamespaces, setSchemaFilterNamespaces] = useState<Set<string>>(new Set());
    const [schemaFilterSearch, setSchemaFilterSearch] = useState('');
    const [schemaSortOrder, setSchemaSortOrder] = useState<SortOrder>('default');

    const schemasPanelRef = useRef<SchemasPanelHandle>(null);

    // ── Loaders ───────────────────────────────────────────────────────────────
    const loadDefinitions = () =>
    {
        setDefsLoading(true);
        setDefsError(null);
        listFieldDefinitions()
            .then(data => setDefinitions(data.filter(d => d.status !== FieldLifecycleStatus.RETIRED)))
            .catch(() => setDefsError('Failed to load fields'))
            .finally(() => setDefsLoading(false));
    };

    const loadSchemas = () =>
    {
        setSchemasLoading(true);
        setSchemasError(null);
        listSchemas()
            .then(data => setSchemas(data.filter(s => s.status !== FieldLifecycleStatus.RETIRED)))
            .catch(() => setSchemasError('Failed to load schemas'))
            .finally(() => setSchemasLoading(false));
    };

    useEffect(() =>
    {
        if (selected === 'fields') loadDefinitions();
        if (selected === 'schemas') loadSchemas();
    }, [selected]);

    // ── Field derived data ────────────────────────────────────────────────────
    const fieldNamespaces = useMemo(() =>
        [...new Set(definitions.map(d => d.namespace))].sort(),
    [definitions]);

    const filteredFieldNamespaces = useMemo(() =>
    {
        const q = fieldFilterSearch.trim().toLowerCase();
        return q ? fieldNamespaces.filter(n => n.toLowerCase().includes(q)) : fieldNamespaces;
    }, [fieldNamespaces, fieldFilterSearch]);

    const filteredDefinitions = useMemo(() =>
    {
        const q = fieldSearch.trim().toLowerCase();
        return definitions
            .filter(d => fieldFilterNamespaces.size === 0 || fieldFilterNamespaces.has(d.namespace))
            .filter(d => fieldFilterTypes.size === 0 ||
                (d.latestContract && fieldFilterTypes.has(d.latestContract.valueType)))
            .filter(d =>
                !q ||
                (d.latestContract?.label ?? '').toLowerCase().includes(q) ||
                d.fieldKey.toLowerCase().includes(q) ||
                d.namespace.toLowerCase().includes(q),
            );
    }, [definitions, fieldSearch, fieldFilterNamespaces, fieldFilterTypes]);

    const fieldFiltersActive = fieldFilterNamespaces.size > 0 || fieldFilterTypes.size > 0;

    // ── Schema derived data ───────────────────────────────────────────────────
    const schemaNamespaces = useMemo(() =>
        [...new Set(schemas.map(s => s.namespace))].sort(),
    [schemas]);

    const filteredSchemaNamespaces = useMemo(() =>
    {
        const q = schemaFilterSearch.trim().toLowerCase();
        return q ? schemaNamespaces.filter(n => n.toLowerCase().includes(q)) : schemaNamespaces;
    }, [schemaNamespaces, schemaFilterSearch]);

    const filteredSchemas = useMemo(() =>
    {
        const q = schemaSearch.trim().toLowerCase();
        let result = schemas
            .filter(s => schemaFilterNamespaces.size === 0 || schemaFilterNamespaces.has(s.namespace))
            .filter(s =>
                !q ||
                s.displayName.toLowerCase().includes(q) ||
                (s.description ?? '').toLowerCase().includes(q) ||
                s.namespace.toLowerCase().includes(q) ||
                s.schemaKey.toLowerCase().includes(q),
            );
        if (schemaSortOrder === 'nameAsc') result = [...result].sort((a, b) => a.displayName.localeCompare(b.displayName));
        if (schemaSortOrder === 'nameDesc') result = [...result].sort((a, b) => b.displayName.localeCompare(a.displayName));
        return result;
    }, [schemas, schemaSearch, schemaFilterNamespaces, schemaSortOrder]);

    const schemaFiltersActive = schemaFilterNamespaces.size > 0;

    // ── Handlers ──────────────────────────────────────────────────────────────
    const handleRetire = async (definition: FieldDefinitionDto) =>
    {
        await retireFieldDefinition(definition.id).catch(() => null);
        loadDefinitions();
    };

    const handleTabChange = (_: unknown, data: {value: TabValue}) =>
    {
        setSelected(data.value);
        setFieldSearch('');
        setFieldFilterNamespaces(new Set());
        setFieldFilterTypes(new Set());
        setFieldFilterSearch('');
        setSchemaSearch('');
        setSchemaFilterNamespaces(new Set());
        setSchemaFilterSearch('');
        setSchemaSortOrder('default');
    };

    // ── Render ────────────────────────────────────────────────────────────────
    return (
        <div id="settings-fields-tab"
             className={styles.container}>
            <div className={styles.stickyBlock}>
                <div className={styles.tabsHeaderRow}>
                    <TabList id="fields-tab-inner-tabs"
                             selectedValue={selected}
                             onTabSelect={handleTabChange}
                             size="small">
                        <Tab id="fields-inner-tab-fields"
                             value="fields">
                            Fields
                        </Tab>
                        <Tab id="fields-inner-tab-schemas"
                             value="schemas">
                            Schemas
                        </Tab>
                    </TabList>

                    {canManage && selected === 'fields' && (
                        <Button id="field-def-create-btn"
                                appearance="secondary"
                                shape="circular"
                                icon={<AddIcon/>}
                                onClick={() => setDialogOpen(true)}>
                            New field
                        </Button>
                    )}
                    {canManage && selected === 'schemas' && (
                        <Button id="schema-create-btn"
                                appearance="secondary"
                                shape="circular"
                                icon={<AddIcon/>}
                                onClick={() => schemasPanelRef.current?.openCreate()}>
                            New schema
                        </Button>
                    )}
                </div>

                {selected === 'fields' && (
                    <>
                        <Text id="fields-panel-description"
                              size={300}
                              className={styles.descriptionText}>
                            Reusable business attributes. Compose them into schemas to attach to exchanges.
                        </Text>
                        <div className={styles.searchRow}>
                            <Field style={{flex: 1}}>
                                <SearchBox id="field-def-search"
                                           placeholder="Search fields"
                                           maxLength={100}
                                           value={fieldSearch}
                                           onChange={(_, d) => setFieldSearch(d.value)}/>
                            </Field>
                            <Popover positioning="below-end"
                                     onOpenChange={(_, {open}) => { if (!open) setFieldFilterSearch(''); }}>
                                <PopoverTrigger disableButtonEnhancement>
                                    <Tooltip content="Filter fields"
                                             relationship="description">
                                        <Button id="field-def-filter-btn"
                                                icon={<FilterIcon/>}
                                                appearance={fieldFiltersActive ? 'primary' : 'subtle'}
                                                shape="circular"/>
                                    </Tooltip>
                                </PopoverTrigger>
                                <PopoverSurface className={styles.filterPopover}>
                                    <div className={styles.filterSection}>
                                        <Text className={styles.filterSectionTitle}>Namespace</Text>
                                        <SearchBox placeholder="Search namespaces"
                                                   size="small"
                                                   value={fieldFilterSearch}
                                                   onChange={(_, d) => setFieldFilterSearch(d.value)}/>
                                        <div className={styles.filterSectionList}>
                                            {filteredFieldNamespaces.map(ns => (
                                                <Checkbox key={ns}
                                                          label={ns}
                                                          checked={fieldFilterNamespaces.has(ns)}
                                                          onChange={() => setFieldFilterNamespaces(prev => toggleSetItem(prev, ns))}/>
                                            ))}
                                            {filteredFieldNamespaces.length === 0 && (
                                                <Text size={200}
                                                      className={styles.emptyText}>
                                                    No namespaces
                                                </Text>
                                            )}
                                        </div>
                                    </div>
                                    <hr className={styles.filterDivider}/>
                                    <div className={styles.filterSection}>
                                        <Text className={styles.filterSectionTitle}>Field type</Text>
                                        <div className={styles.filterSectionList}>
                                            {Object.values(FieldValueType).map(type => (
                                                <Checkbox key={type}
                                                          label={VALUE_TYPE_LABELS[type]}
                                                          checked={fieldFilterTypes.has(type)}
                                                          onChange={() => setFieldFilterTypes(prev => toggleSetItem(prev, type))}/>
                                            ))}
                                        </div>
                                    </div>
                                </PopoverSurface>
                            </Popover>
                            <ViewModeToggle value={fieldViewMode}
                                            onChange={setFieldViewMode}/>
                        </div>
                    </>
                )}

                {selected === 'schemas' && (
                    <>
                        <Text id="schemas-panel-description"
                              size={300}
                              className={styles.descriptionText}>
                            Business schemas group fields into a case type that exchange creators can select.
                        </Text>
                        <div className={styles.searchRow}>
                            <Field style={{flex: 1}}>
                                <SearchBox id="schema-search"
                                           placeholder="Search schemas"
                                           maxLength={100}
                                           value={schemaSearch}
                                           onChange={(_, d) => setSchemaSearch(d.value)}/>
                            </Field>
                            {schemaNamespaces.length > 0 && (
                                <Popover positioning="below-end"
                                         onOpenChange={(_, {open}) => { if (!open) setSchemaFilterSearch(''); }}>
                                    <PopoverTrigger disableButtonEnhancement>
                                        <Tooltip content="Filter schemas"
                                                 relationship="description">
                                            <Button id="schema-filter-btn"
                                                    icon={<FilterIcon/>}
                                                    appearance={schemaFiltersActive ? 'primary' : 'subtle'}
                                                    shape="circular"/>
                                        </Tooltip>
                                    </PopoverTrigger>
                                    <PopoverSurface className={styles.filterPopover}>
                                        <div className={styles.filterSection}>
                                            <Text className={styles.filterSectionTitle}>Namespace</Text>
                                            <SearchBox placeholder="Search namespaces"
                                                       size="small"
                                                       value={schemaFilterSearch}
                                                       onChange={(_, d) => setSchemaFilterSearch(d.value)}/>
                                            <div className={styles.filterSectionList}>
                                                {filteredSchemaNamespaces.map(ns => (
                                                    <Checkbox key={ns}
                                                              label={ns}
                                                              checked={schemaFilterNamespaces.has(ns)}
                                                              onChange={() => setSchemaFilterNamespaces(prev => toggleSetItem(prev, ns))}/>
                                                ))}
                                                {filteredSchemaNamespaces.length === 0 && (
                                                    <Text size={200}
                                                          className={styles.emptyText}>
                                                        No namespaces
                                                    </Text>
                                                )}
                                            </div>
                                        </div>
                                    </PopoverSurface>
                                </Popover>
                            )}
                            <Menu>
                                <MenuTrigger>
                                    <Tooltip content={
                                        schemaSortOrder === 'nameAsc' ? 'Name (A-Z)' :
                                        schemaSortOrder === 'nameDesc' ? 'Name (Z-A)' :
                                        'Default order'
                                    }
                                             relationship="description">
                                        <Button id="schema-sort-btn"
                                                icon={schemaSortOrder === 'nameDesc' ? <SortDownIcon/> : <SortUpIcon/>}
                                                appearance={schemaSortOrder !== 'default' ? 'primary' : 'subtle'}
                                                shape="circular"/>
                                    </Tooltip>
                                </MenuTrigger>
                                <MenuPopover>
                                    <MenuList>
                                        <MenuItem icon={schemaSortOrder === 'default' ? <CheckmarkIcon/> : undefined}
                                                  onClick={() => setSchemaSortOrder('default')}>
                                            Default order
                                        </MenuItem>
                                        <MenuItem icon={schemaSortOrder === 'nameAsc' ? <CheckmarkIcon/> : undefined}
                                                  onClick={() => setSchemaSortOrder('nameAsc')}>
                                            Name (A-Z)
                                        </MenuItem>
                                        <MenuItem icon={schemaSortOrder === 'nameDesc' ? <CheckmarkIcon/> : undefined}
                                                  onClick={() => setSchemaSortOrder('nameDesc')}>
                                            Name (Z-A)
                                        </MenuItem>
                                    </MenuList>
                                </MenuPopover>
                            </Menu>
                            <ViewModeToggle value={schemaViewMode}
                                            onChange={setSchemaViewMode}/>
                        </div>
                    </>
                )}
            </div>

            {selected === 'fields' && (
                <FieldDefinitionsPanel definitions={filteredDefinitions}
                                       viewMode={fieldViewMode}
                                       canManage={canManage}
                                       loading={defsLoading}
                                       error={defsError}
                                       onRetire={handleRetire}/>
            )}
            {selected === 'schemas' && (
                <SchemasPanel ref={schemasPanelRef}
                              schemas={filteredSchemas}
                              loading={schemasLoading}
                              error={schemasError}
                              viewMode={schemaViewMode}
                              canManage={canManage}
                              onRefresh={loadSchemas}/>
            )}

            <FieldDefinitionDialog open={dialogOpen}
                                   existingNamespaces={fieldNamespaces}
                                   onClose={() => setDialogOpen(false)}
                                   onSaved={() => { setDialogOpen(false); loadDefinitions(); }}/>
        </div>
    );
};

export default FieldsTab;
