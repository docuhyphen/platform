import {makeStyles, shorthands, tokens} from '@fluentui/react-components';

export const useMyGroupsTabStyles = makeStyles({
    container: {
        display: 'flex',
        flexDirection: 'column',
        ...shorthands.gap(tokens.spacingHorizontalM),
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
        ...shorthands.padding(tokens.spacingHorizontalXXXL),
    },
    error: {
        color: tokens.colorPaletteRedForeground1,
        ...shorthands.padding(tokens.spacingHorizontalS),
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
        gap: tokens.spacingHorizontalM,
    },
    dialogContentColumnLarge: {
        display: 'flex',
        flexDirection: 'column',
        gap: tokens.spacingHorizontalL,
    },
    manageSurface: {
        minWidth: '480px',
    },
    memberList: {
        marginTop: tokens.spacingVerticalS,
        display: 'flex',
        flexDirection: 'column',
        gap: tokens.spacingHorizontalXXS,
    },
    memberRow: {
        display: 'flex',
        alignItems: 'center',
        gap: tokens.spacingHorizontalS,
        paddingTop: tokens.spacingVerticalXS,
        paddingBottom: tokens.spacingVerticalXS,
    },
    memberName: {
        flex: 1,
    },
    addMembersRow: {
        display: 'flex',
        alignItems: 'center',
        gap: tokens.spacingHorizontalS,
        flexWrap: 'wrap',
    },
});
