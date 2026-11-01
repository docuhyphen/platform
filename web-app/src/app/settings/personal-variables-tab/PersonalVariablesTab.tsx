import {forwardRef, useImperativeHandle} from 'react';
import {Text} from '@fluentui/react-components';
import {ViewMode} from '../../models/models';
import {usePersonalVariablesTabStyles} from './PersonalVariablesTabStyles';
import {personalVariableIds} from '../variables-tab/variableIds.ts';
import {useCopiedToken} from '../variables-tab/useCopiedToken.ts';
import {useVariableList} from '../variables-tab/useVariableList.ts';
import {useVariableEditor} from '../variables-tab/useVariableEditor.ts';
import ScopedVariablesList from '../variables-tab/scoped-variables-list/ScopedVariablesList.tsx';
import VariableDrawer from '../variables-tab/variable-drawer/VariableDrawer.tsx';

export interface PersonalVariablesTabHandle
{
    openCreate: () => void;
}

interface PersonalVariablesTabProps
{
    viewMode?: ViewMode;
}

const PersonalVariablesTab = forwardRef<PersonalVariablesTabHandle, PersonalVariablesTabProps>(({viewMode = 'cards'}, ref) =>
{
    const styles = usePersonalVariablesTabStyles();
    const list = useVariableList('PERSONAL', 'Failed to load personal variables');
    const editor = useVariableEditor('PERSONAL', list.load);
    const {copiedToken, copy} = useCopiedToken();

    useImperativeHandle(ref, () => ({openCreate: editor.openCreate}));

    return (
        <>
            <div id={"personal-variables-tab-div-1"}
                 className={styles.container}>
                <Text
                    id={"personal-variables-tab-text-1"}
                    size={300}
                    className={styles.descriptionText}
                >
                    Private key-value pairs only you can see and use. Override them when creating an Exchange.
                </Text>

                <ScopedVariablesList ids={personalVariableIds}
                                     variables={list.variables}
                                     loading={list.loading}
                                     error={list.error}
                                     emptyText="No personal variables yet."
                                     viewMode={viewMode}
                                     canManage={true}
                                     copiedToken={copiedToken}
                                     onCopy={copy}
                                     onEdit={editor.openEdit}
                                     onDelete={list.remove}/>
            </div>

            <VariableDrawer ids={personalVariableIds}
                            editor={editor}
                            createTitle="New Personal Variable"
                            keyPlaceholder="e.g. MY_COMPANY"
                            valuePlaceholder="e.g. Smith & Co"/>
        </>
    );
});

export default PersonalVariablesTab;
