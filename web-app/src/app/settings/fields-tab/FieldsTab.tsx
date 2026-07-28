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
    FieldScopeKind,
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
import FieldsPagination from './fields-pagination/FieldsPagination';

type SortOrder = 'default' | 'nameAsc' | 'nameDesc';
const PAGE_SIZE = 12;

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
    const canManageOrganization = !!appUserPersonOrganization?.isActive &&
        hasCapability(Capability.ORG_POLICY_MANAGE);
    const canManageField = (definition: FieldDefinitionDto) =>
        canManageOrganization && definition.scopeKind === FieldScopeKind.ORGANIZATION;
    const canManageSchema = (schema: SchemaDefinitionDto) =>
        canManageOrganization && schema.scopeKind === FieldScopeKind.ORGANIZATION;

    const [selected, setSelected] = useState<TabValue>('fields');

    const [definitions, setDefinitions] = useState<FieldDefinitionDto[]>([]);
    const [defsLoading, setDefsLoading] = useState(false);
    const [defsError, setDefsError] = useState<string | null>(null);
    const [dialogOpen, setDialogOpen] = useState(false);
    const [fieldSearch, setFieldSearch] = useState('');
    const [fieldViewMode, setFieldViewMode] = useState<ViewMode>('cards');
    const [fieldFilterNamespaces, setFieldFilterNamespaces] = useState<Set<string>>(new Set());
    const [fieldFilterTypes, setFieldFilterTypes] = useState<Set<FieldValueType>>(new Set());
    const [fieldFilterSearch, setFieldFilterSearch] = useState('');
    const [fieldSortOrder, setFieldSortOrder] = useState<SortOrder>('default');
    const [fieldCurrentPage, setFieldCurrentPage] = useState(0);

    const [schemas, setSchemas] = useState<SchemaDefinitionDto[]>([]);
    const [schemasLoading, setSchemasLoading] = useState(false);
    const [schemasError, setSchemasError] = useState<string | null>(null);
    const [schemaSearch, setSchemaSearch] = useState('');
    const [schemaViewMode, setSchemaViewMode] = useState<ViewMode>('cards');
    const [schemaFilterNamespaces, setSchemaFilterNamespaces] = useState<Set<string>>(new Set());
    const [schemaFilterSearch, setSchemaFilterSearch] = useState('');
    const [schemaSortOrder, setSchemaSortOrder] = useState<SortOrder>('default');
    const [schemaCurrentPage, setSchemaCurrentPage] = useState(0);

    const schemasPanelRef = useRef<SchemasPanelHandle>(null);

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
        let result = definitions
            .filter(d => fieldFilterNamespaces.size === 0 || fieldFilterNamespaces.has(d.namespace))
            .filter(d => fieldFilterTypes.size === 0 ||
                (d.latestContract && fieldFilterTypes.has(d.latestContract.valueType)))
            .filter(d =>
                !q ||
                (d.latestContract?.label ?? '').toLowerCase().includes(q) ||
                d.fieldKey.toLowerCase().includes(q) ||
                d.namespace.toLowerCase().includes(q),
            );
        if (fieldSortOrder === 'nameAsc')
        {
            result = [...result].sort((a, b) =>
                (a.latestContract?.label ?? a.fieldKey).localeCompare(b.latestContract?.label ?? b.fieldKey));
        }
        if (fieldSortOrder === 'nameDesc')
        {
            result = [...result].sort((a, b) =>
                (b.latestContract?.label ?? b.fieldKey).localeCompare(a.latestContract?.label ?? a.fieldKey));
        }
        return result;
    }, [definitions, fieldSearch, fieldFilterNamespaces, fieldFilterTypes, fieldSortOrder]);

    const fieldTotalPages = Math.ceil(filteredDefinitions.length / PAGE_SIZE);
    const visibleDefinitions = filteredDefinitions.slice(
        fieldCurrentPage * PAGE_SIZE,
        (fieldCurrentPage + 1) * PAGE_SIZE,
    );
    const fieldFiltersActive = fieldFilterNamespaces.size > 0 || fieldFilterTypes.size > 0;

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

    const schemaTotalPages = Math.ceil(filteredSchemas.length / PAGE_SIZE);
    const visibleSchemas = filteredSchemas.slice(
        schemaCurrentPage * PAGE_SIZE,
        (schemaCurrentPage + 1) * PAGE_SIZE,
    );
    const schemaFiltersActive = schemaFilterNamespaces.size > 0;

    useEffect(() =>
    {
        if (fieldCurrentPage > 0 && fieldCurrentPage >= Math.max(fieldTotalPages, 1))
        {
            setFieldCurrentPage(Math.max(fieldTotalPages - 1, 0));
        }
    }, [fieldCurrentPage, fieldTotalPages]);

    useEffect(() =>
    {
        if (schemaCurrentPage > 0 && schemaCurrentPage >= Math.max(schemaTotalPages, 1))
        {
            setSchemaCurrentPage(Math.max(schemaTotalPages - 1, 0));
        }
    }, [schemaCurrentPage, schemaTotalPages]);

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
        setFieldSortOrder('default');
        setFieldCurrentPage(0);
        setSchemaSearch('');
        setSchemaFilterNamespaces(new Set());
        setSchemaFilterSearch('');
        setSchemaSortOrder('default');
        setSchemaCurrentPage(0);
    };

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

                    {canManageOrganization && selected === 'fields' && (
                        <Button id="field-def-create-btn"
                                appearance="subtle"
                                shape="circular"
                                icon={<AddIcon/>}
                                onClick={() => setDialogOpen(true)}>
                            New field
                        </Button>
                    )}
                    {canManageOrganization && selected === 'schemas' && (
                        <Button id="schema-create-btn"
                                appearance="subtle"
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
                            <div className={styles.searchRowInputs}>
                                <Field className={styles.searchField}>
                                    <SearchBox id="field-def-search"
                                               placeholder="Search fields"
                                               maxLength={100}
                                               value={fieldSearch}
                                               onChange={(_, d) =>
                                               {
                                                   setFieldSearch(d.value);
                                                   setFieldCurrentPage(0);
                                               }}/>
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
                                                              onChange={() =>
                                                              {
                                                                  setFieldFilterNamespaces(prev => toggleSetItem(prev, ns));
                                                                  setFieldCurrentPage(0);
                                                              }}/>
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
                                                              onChange={() =>
                                                              {
                                                                  setFieldFilterTypes(prev => toggleSetItem(prev, type));
                                                                  setFieldCurrentPage(0);
                                                              }}/>
                                                ))}
                                            </div>
                                        </div>
                                    </PopoverSurface>
                                </Popover>
                                <Menu>
                                    <MenuTrigger>
                                        <Tooltip content={
                                            fieldSortOrder === 'nameAsc' ? 'Name (A-Z)' :
                                            fieldSortOrder === 'nameDesc' ? 'Name (Z-A)' :
                                            'Default order'
                                        }
                                                 relationship="description">
                                            <Button id="field-def-sort-btn"
                                                    icon={fieldSortOrder === 'nameDesc' ? <SortDownIcon/> : <SortUpIcon/>}
                                                    appearance={fieldSortOrder !== 'default' ? 'primary' : 'subtle'}
                                                    shape="circular"/>
                                        </Tooltip>
                                    </MenuTrigger>
                                    <MenuPopover>
                                        <MenuList>
                                            <MenuItem id="field-def-sort-default"
                                                      icon={fieldSortOrder === 'default' ? <CheckmarkIcon/> : undefined}
                                                      onClick={() =>
                                                      {
                                                          setFieldSortOrder('default');
                                                          setFieldCurrentPage(0);
                                                      }}>
                                                Default order
                                            </MenuItem>
                                            <MenuItem id="field-def-sort-name-asc"
                                                      icon={fieldSortOrder === 'nameAsc' ? <CheckmarkIcon/> : undefined}
                                                      onClick={() =>
                                                      {
                                                          setFieldSortOrder('nameAsc');
                                                          setFieldCurrentPage(0);
                                                      }}>
                                                Name (A-Z)
                                            </MenuItem>
                                            <MenuItem id="field-def-sort-name-desc"
                                                      icon={fieldSortOrder === 'nameDesc' ? <CheckmarkIcon/> : undefined}
                                                      onClick={() =>
                                                      {
                                                          setFieldSortOrder('nameDesc');
                                                          setFieldCurrentPage(0);
                                                      }}>
                                                Name (Z-A)
                                            </MenuItem>
                                        </MenuList>
                                    </MenuPopover>
                                </Menu>
                            </div>
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
                            <div className={styles.searchRowInputs}>
                                <Field className={styles.searchField}>
                                    <SearchBox id="schema-search"
                                               placeholder="Search schemas"
                                               maxLength={100}
                                               value={schemaSearch}
                                               onChange={(_, d) =>
                                               {
                                                   setSchemaSearch(d.value);
                                                   setSchemaCurrentPage(0);
                                               }}/>
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
                                                                  onChange={() =>
                                                                  {
                                                                      setSchemaFilterNamespaces(prev => toggleSetItem(prev, ns));
                                                                      setSchemaCurrentPage(0);
                                                                  }}/>
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
                                            <MenuItem id="schema-sort-default"
                                                      icon={schemaSortOrder === 'default' ? <CheckmarkIcon/> : undefined}
                                                      onClick={() =>
                                                      {
                                                          setSchemaSortOrder('default');
                                                          setSchemaCurrentPage(0);
                                                      }}>
                                                Default order
                                            </MenuItem>
                                            <MenuItem id="schema-sort-name-asc"
                                                      icon={schemaSortOrder === 'nameAsc' ? <CheckmarkIcon/> : undefined}
                                                      onClick={() =>
                                                      {
                                                          setSchemaSortOrder('nameAsc');
                                                          setSchemaCurrentPage(0);
                                                      }}>
                                                Name (A-Z)
                                            </MenuItem>
                                            <MenuItem id="schema-sort-name-desc"
                                                      icon={schemaSortOrder === 'nameDesc' ? <CheckmarkIcon/> : undefined}
                                                      onClick={() =>
                                                      {
                                                          setSchemaSortOrder('nameDesc');
                                                          setSchemaCurrentPage(0);
                                                      }}>
                                                Name (Z-A)
                                            </MenuItem>
                                        </MenuList>
                                    </MenuPopover>
                                </Menu>
                            </div>
                            <ViewModeToggle value={schemaViewMode}
                                            onChange={setSchemaViewMode}/>
                        </div>
                    </>
                )}
            </div>

            <div className={styles.scrollableContent}>
            {selected === 'fields' && (
                <FieldDefinitionsPanel definitions={visibleDefinitions}
                                       viewMode={fieldViewMode}
                                       canManage={canManageField}
                                       loading={defsLoading}
                                       error={defsError}
                                       onRetire={handleRetire}/>
            )}
            {selected === 'schemas' && (
                <SchemasPanel ref={schemasPanelRef}
                              schemas={visibleSchemas}
                              loading={schemasLoading}
                              error={schemasError}
                              viewMode={schemaViewMode}
                              canManage={canManageSchema}
                              onRefresh={loadSchemas}/>
            )}
            </div>
            {selected === 'fields' && !defsLoading && !defsError && filteredDefinitions.length > 0 && (
                <FieldsPagination
                    currentPage={fieldCurrentPage}
                    totalPages={Math.max(fieldTotalPages, 1)}
                    totalItems={filteredDefinitions.length}
                    pageSize={PAGE_SIZE}
                    itemLabel={"fields"}
                    onPageChange={setFieldCurrentPage}
                />
            )}
            {selected === 'schemas' && !schemasLoading && !schemasError && filteredSchemas.length > 0 && (
                <FieldsPagination
                    currentPage={schemaCurrentPage}
                    totalPages={Math.max(schemaTotalPages, 1)}
                    totalItems={filteredSchemas.length}
                    pageSize={PAGE_SIZE}
                    itemLabel={"schemas"}
                    onPageChange={setSchemaCurrentPage}
                />
            )}

            <FieldDefinitionDialog open={dialogOpen}
                                   existingNamespaces={fieldNamespaces}
                                   onClose={() => setDialogOpen(false)}
                                   onSaved={() => { setDialogOpen(false); loadDefinitions(); }}/>
        </div>
    );
};

export default FieldsTab;
