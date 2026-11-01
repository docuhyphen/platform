import {makeStyles, tokens} from '@fluentui/react-components';

export const useDocumentLibraryTableStyles = makeStyles({
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
});
