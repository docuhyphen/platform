import {makeStyles, shorthands, tokens} from '@fluentui/react-components';

export const useMyGroupsTabStyles = makeStyles({
    container: {
        display: 'flex',
        flexDirection: 'column',
        ...shorthands.gap('12px'),
    },
    header: {
        display: 'flex',
        justifyContent: 'space-between',
        alignItems: 'center',
    },
    table: {
        width: '100%',
        overflowX: 'auto',
        minWidth: 0,
    },
    loading: {
        display: 'flex',
        justifyContent: 'center',
        ...shorthands.padding('32px'),
    },
    error: {
        color: tokens.colorPaletteRedForeground1,
        ...shorthands.padding('8px'),
    },
    statusCell: {
        width: '90px',
        minWidth: '90px',
        maxWidth: '90px',
    },
    actionsCell: {
        width: '60px',
        minWidth: '60px',
        maxWidth: '60px',
    },
    dialogContentColumn: {
        display: 'flex',
        flexDirection: 'column',
        gap: '12px',
    },
    dialogContentColumnLarge: {
        display: 'flex',
        flexDirection: 'column',
        gap: '16px',
    },
    manageSurface: {
        minWidth: '480px',
    },
    memberList: {
        marginTop: '8px',
        display: 'flex',
        flexDirection: 'column',
        gap: '2px',
    },
    memberRow: {
        display: 'flex',
        alignItems: 'center',
        gap: '8px',
        paddingTop: '4px',
        paddingBottom: '4px',
    },
    memberName: {
        flex: 1,
    },
});
