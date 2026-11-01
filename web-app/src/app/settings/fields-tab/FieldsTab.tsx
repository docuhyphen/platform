import {useRef, useState} from 'react';
import {TabValue} from '@fluentui/react-components';
import {useAuth} from '../../../context/AuthContext';
import {Capability, FieldDefinitionDto, FieldScopeKind, SchemaDefinitionDto} from '../../models/models';
import {useFieldsTabStyles} from './FieldsTabStyles';
import {FIELDS_PAGE_SIZE} from './fieldsListTypes';
import {useFieldDefinitionsList} from './useFieldDefinitionsList';
import {useSchemasList} from './useSchemasList';
import FieldsTabHeader from './fields-tab-header/FieldsTabHeader';
import FieldDefinitionsToolbar from './field-definitions-toolbar/FieldDefinitionsToolbar';
import SchemasToolbar from './schemas-toolbar/SchemasToolbar';
import FieldDefinitionsPanel from './field-definitions-panel/FieldDefinitionsPanel';
import SchemasPanel, {SchemasPanelHandle} from './schemas-panel/SchemasPanel';
import FieldDefinitionDialog from './field-definition-dialog/FieldDefinitionDialog';
import FieldsPagination from './fields-pagination/FieldsPagination';

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
    const [dialogOpen, setDialogOpen] = useState(false);
    const fieldsList = useFieldDefinitionsList(selected === 'fields');
    const schemasList = useSchemasList(selected === 'schemas');
    const schemasPanelRef = useRef<SchemasPanelHandle>(null);

    const handleTabChange = (_: unknown, data: {value: TabValue}) =>
    {
        setSelected(data.value);
        fieldsList.reset();
        schemasList.reset();
    };

    return (
        <div id="settings-fields-tab"
             className={styles.container}>
            <div id={"fields-tab-div-1"}
                 className={styles.stickyBlock}>
                <FieldsTabHeader selected={selected}
                                 canManageOrganization={canManageOrganization}
                                 onTabChange={handleTabChange}
                                 onCreateField={() => setDialogOpen(true)}
                                 onCreateSchema={() => schemasPanelRef.current?.openCreate()}/>

                {selected === 'fields' && <FieldDefinitionsToolbar list={fieldsList}/>}

                {selected === 'schemas' && <SchemasToolbar list={schemasList}/>}
            </div>

            <div id={"fields-tab-div-13"}
                 className={styles.scrollableContent}>
            {selected === 'fields' && (
                <FieldDefinitionsPanel definitions={fieldsList.visible}
                                       viewMode={fieldsList.controls.viewMode}
                                       canManage={canManageField}
                                       loading={fieldsList.loading}
                                       error={fieldsList.error}
                                       onRetire={fieldsList.retire}/>
            )}
            {selected === 'schemas' && (
                <SchemasPanel ref={schemasPanelRef}
                              schemas={schemasList.visible}
                              loading={schemasList.loading}
                              error={schemasList.error}
                              viewMode={schemasList.controls.viewMode}
                              canManage={canManageSchema}
                              onRefresh={schemasList.load}/>
            )}
            </div>
            {selected === 'fields' && !fieldsList.loading && !fieldsList.error && fieldsList.filtered.length > 0 && (
                <FieldsPagination
                    currentPage={fieldsList.controls.currentPage}
                    totalPages={Math.max(fieldsList.totalPages, 1)}
                    totalItems={fieldsList.filtered.length}
                    pageSize={FIELDS_PAGE_SIZE}
                    itemLabel={"fields"}
                    onPageChange={fieldsList.controls.setCurrentPage}
                />
            )}
            {selected === 'schemas' && !schemasList.loading && !schemasList.error && schemasList.filtered.length > 0 && (
                <FieldsPagination
                    currentPage={schemasList.controls.currentPage}
                    totalPages={Math.max(schemasList.totalPages, 1)}
                    totalItems={schemasList.filtered.length}
                    pageSize={FIELDS_PAGE_SIZE}
                    itemLabel={"schemas"}
                    onPageChange={schemasList.controls.setCurrentPage}
                />
            )}

            <FieldDefinitionDialog open={dialogOpen}
                                   existingNamespaces={fieldsList.namespaces}
                                   onClose={() => setDialogOpen(false)}
                                   onSaved={() => { setDialogOpen(false); fieldsList.load(); }}/>
        </div>
    );
};

export default FieldsTab;
