import {forwardRef, useImperativeHandle} from 'react';
import {useAuth} from '../../../context/AuthContext';
import {Text} from '@fluentui/react-components';
import {Capability, ViewMode} from '../../models/models';
import {useOrganizationVariablesTabStyles} from './OrganizationVariablesTabStyles';
import {organizationVariableIds} from '../variables-tab/variableIds.ts';
import {useCopiedToken} from '../variables-tab/useCopiedToken.ts';
import {useVariableList} from '../variables-tab/useVariableList.ts';
import {useVariableEditor} from '../variables-tab/useVariableEditor.ts';
import ScopedVariablesList from '../variables-tab/scoped-variables-list/ScopedVariablesList.tsx';
import VariableDrawer from '../variables-tab/variable-drawer/VariableDrawer.tsx';

export interface OrganizationVariablesTabHandle
{
    openCreate: () => void;
}

interface OrganizationVariablesTabProps
{
    viewMode?: ViewMode;
}

const OrganizationVariablesTab = forwardRef<OrganizationVariablesTabHandle, OrganizationVariablesTabProps>(({viewMode = 'cards'}, ref) =>
{
    const styles = useOrganizationVariablesTabStyles();
    const {appUserPersonOrganization, hasCapability} = useAuth();
    const canManage =
        appUserPersonOrganization?.isActive &&
        hasCapability(Capability.ORG_POLICY_MANAGE);
    const list = useVariableList('ORG', 'Failed to load variables');
    const editor = useVariableEditor('ORG', list.load);
    const {copiedToken, copy} = useCopiedToken();

    useImperativeHandle(ref, () => ({openCreate: editor.openCreate}));

    return (
        <>
            <div id={"organization-variables-tab-div-1"}
                 className={styles.container}>
                <Text
                    id={"organization-variables-tab-text-1"}
                    size={300}
                    className={styles.descriptionText}
                >
                    Reusable key-value pairs shared across your organization. Users can override them per Exchange.
                </Text>

                <ScopedVariablesList ids={organizationVariableIds}
                                     variables={list.variables}
                                     loading={list.loading}
                                     error={list.error}
                                     emptyText="No org variables yet."
                                     viewMode={viewMode}
                                     canManage={!!canManage}
                                     copiedToken={copiedToken}
                                     onCopy={copy}
                                     onEdit={editor.openEdit}
                                     onDelete={list.remove}/>
            </div>

            <VariableDrawer ids={organizationVariableIds}
                            editor={editor}
                            createTitle="New Org Variable"
                            keyPlaceholder="e.g. CLIENT_NAME"
                            valuePlaceholder="e.g. Acme Corp"/>
        </>
    );
});

export default OrganizationVariablesTab;
