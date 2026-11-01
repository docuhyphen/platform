import {makeStyles, tokens} from '@fluentui/react-components';

export const useSequenceTableStyles = makeStyles({
    codeToken: {
        fontFamily: 'monospace',
        fontSize: tokens.fontSizeBase200,
        color: tokens.colorNeutralForeground2,
    },
    table: {
        width: '100%',
        borderCollapse: 'collapse',
    },
    th: {
        textAlign: 'left',
        padding: `${tokens.spacingVerticalSNudge} ${tokens.spacingHorizontalM}`,
        fontSize: tokens.fontSizeBase200,
        fontWeight: tokens.fontWeightSemibold,
        color: tokens.colorNeutralForeground3,
        borderBottom: `1px solid ${tokens.colorNeutralStroke1}`,
        whiteSpace: 'nowrap',
    },
    tr: {
        borderBottom: `1px solid ${tokens.colorNeutralStroke2}`,
        ':hover': {backgroundColor: tokens.colorNeutralBackground2},
    },
    td: {
        padding: `${tokens.spacingVerticalS} ${tokens.spacingHorizontalM}`,
        verticalAlign: 'middle',
    },
});
