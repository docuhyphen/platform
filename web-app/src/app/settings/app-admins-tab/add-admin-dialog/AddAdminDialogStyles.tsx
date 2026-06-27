import {makeStyles, shorthands, tokens} from '@fluentui/react-components';

export const useAddAdminDialogStyles = makeStyles({
    surface: {
        minWidth: '480px',
        maxWidth: '600px',
    },
    content: {
        display: 'flex',
        flexDirection: 'column',
        ...shorthands.gap('16px'),
        marginTop: '1rem',
        marginBottom: '1rem',
    },
    errorText: {
        color: tokens.colorStatusDangerForeground1,
    },
    selectedBadges: {
        display: 'flex',
        ...shorthands.gap('6px'),
        flexWrap: 'wrap',
    },
    searchResults: {
        ...shorthands.border('1px', 'solid', tokens.colorNeutralStroke1),
        borderRadius: tokens.borderRadiusMedium,
        maxHeight: '260px',
        overflowY: 'auto',
    },
    searchPadding: {
        ...shorthands.padding('12px'),
    },
    checkboxCell: {
        width: '44px',
    },
    emailCell: {
        maxWidth: '200px',
        overflow: 'hidden',
        textOverflow: 'ellipsis',
        whiteSpace: 'nowrap',
    },
    clickableRow: {
        cursor: 'pointer',
    },
});
