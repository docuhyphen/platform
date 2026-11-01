import {Spinner, Text} from '@fluentui/react-components';
import {VariableDefinitionDto, ViewMode} from '../../../models/models';
import {VariableIdScheme} from '../variableIds.ts';
import VariableCard from '../variable-card/VariableCard.tsx';
import VariableTable from '../variable-table/VariableTable.tsx';
import {useScopedVariablesListStyles} from './ScopedVariablesListStyles.tsx';

interface ScopedVariablesListProps
{
    ids: VariableIdScheme;
    variables: VariableDefinitionDto[];
    loading: boolean;
    error: string | null;
    emptyText: string;
    viewMode: ViewMode;
    canManage: boolean;
    copiedToken: string | null;
    onCopy: (token: string) => void;
    onEdit: (v: VariableDefinitionDto) => void;
    onDelete: (v: VariableDefinitionDto) => void;
}

const ScopedVariablesList = (
    {
        ids,
        variables,
        loading,
        error,
        emptyText,
        viewMode,
        canManage,
        copiedToken,
        onCopy,
        onEdit,
        onDelete,
    }: ScopedVariablesListProps) =>
{
    const styles = useScopedVariablesListStyles();
    const hasVariables = !loading && !error && variables.length > 0;

    return (
        <>
            {loading && <Spinner id={`${ids.tab}-spinner-1`}
                                 size="small"
                                 label="Loading…"/>}
            {!loading && error && (
                <Text id={`${ids.tab}-text-2`}
                      className={styles.errorText}>{error}</Text>
            )}
            {!loading && !error && variables.length === 0 && (
                <Text id={`${ids.tab}-text-3`}
                      className={styles.emptyText}>{emptyText}</Text>
            )}
            {hasVariables && viewMode === 'cards' && variables.map(v => (
                <VariableCard key={v.id}
                              ids={ids}
                              variable={v}
                              canManage={canManage}
                              copiedToken={copiedToken}
                              onCopy={onCopy}
                              onEdit={onEdit}
                              onDelete={onDelete}/>
            ))}
            {hasVariables && viewMode === 'table' && (
                <VariableTable ids={ids}
                               variables={variables}
                               canManage={canManage}
                               copiedToken={copiedToken}
                               onCopy={onCopy}
                               onEdit={onEdit}
                               onDelete={onDelete}/>
            )}
        </>
    );
};

export default ScopedVariablesList;
