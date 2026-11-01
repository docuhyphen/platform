import {
    Badge,
    Spinner,
    Table,
    TableBody,
    TableCell,
    TableHeader,
    TableHeaderCell,
    TableRow,
    Text,
} from '@fluentui/react-components';
import CopyTokenButton from '../copy-token-button/CopyTokenButton.tsx';
import {useCopiedToken} from '../useCopiedToken.ts';
import {useSystemVariables} from './useSystemVariables.ts';
import {usePlatformVariablesViewStyles} from './PlatformVariablesViewStyles.tsx';

const SYSTEM_VARIABLE_COLUMNS = ['Token', 'Description', 'Example'];

const PlatformVariablesView = () =>
{
    const styles = usePlatformVariablesViewStyles();
    const {systemVars, loading, error} = useSystemVariables();
    const {copiedToken, copy} = useCopiedToken();

    return (
        <div id={"variables-tab-div-1"}
             className={styles.platformContainer}>
            <div id={"variables-tab-div-2"}>
                <Text
                    id={"variables-tab-text-1"}
                    size={500}
                    weight="semibold"
                    block
                    className={styles.systemVarTitle}
                >
                    System Variables
                </Text>
                <Text
                    id={"variables-tab-text-2"}
                    size={300}
                    className={styles.systemVarSubtitle}
                >
                    Built-in tokens resolved automatically at Exchange creation time. Read-only, no configuration needed.
                </Text>
            </div>
            {loading && <Spinner id={"variables-tab-spinner-1"}
                                 size="small"/>}
            {!loading && error && (
                <Text id={"variables-tab-text-3"}
                      className={styles.errorText}>{error}</Text>
            )}
            {!loading && !error && (
                <Table id={"variables-tab-table-1"}
                       size="small">
                    <TableHeader id={"variables-tab-table-header-1"}>
                        <TableRow id={"variables-tab-table-row-1"}>
                            {SYSTEM_VARIABLE_COLUMNS.map(c => <TableHeaderCell id={`variables-tab-table-header-cell-1-${c}`}
                                                                               key={c}>{c}</TableHeaderCell>)}
                        </TableRow>
                    </TableHeader>
                    <TableBody id={"variables-tab-table-body-1"}>
                        {systemVars.map(sv => (
                            <TableRow id={`variables-tab-table-row-2-${sv.token}`}
                                      key={sv.token}>
                                <TableCell id={"variables-tab-table-cell-1"}>
                                    <div id={"variables-tab-div-3"}
                                         className={styles.tokenCell}>
                                        <CopyTokenButton id={`button-system-var-copy-${sv.token}`}
                                                         token={sv.token}
                                                         copiedToken={copiedToken}
                                                         ariaLabel={`Copy system variable ${sv.token}`}
                                                         onCopy={copy}/>
                                        <code id={"variables-tab-code-1"}
                                              className={styles.codeCell}>{sv.token}</code>
                                    </div>
                                </TableCell>
                                <TableCell id={"variables-tab-table-cell-2"}>
                                    <Text id={"variables-tab-text-4"}
                                          size={200}>{sv.description}</Text>
                                </TableCell>
                                <TableCell id={"variables-tab-table-cell-3"}>
                                    <Badge
                                        id={"variables-tab-badge-1"}
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

export default PlatformVariablesView;
