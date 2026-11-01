import {makeStyles, tokens} from '@fluentui/react-components';

export const useVariableTableStyles = makeStyles({
    codeKey: {
        fontFamily: 'monospace',
        fontWeight: tokens.fontWeightSemibold,
    },
    tokenCell: {
        display: 'flex',
        alignItems: 'center',
        gap: tokens.spacingHorizontalS,
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
