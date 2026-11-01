import {makeStyles, tokens} from '@fluentui/react-components';

export const useManageGroupDialogStyles = makeStyles({
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
