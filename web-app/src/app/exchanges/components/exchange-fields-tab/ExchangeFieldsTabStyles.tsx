import {makeStyles, tokens} from '@fluentui/react-components';

export const useExchangeFieldsTabStyles = makeStyles({
    container: {
        display: 'flex',
        flexDirection: 'column',
        gap: '16px',
        padding: '4px',
    },
    headerRow: {
        display: 'flex',
        justifyContent: 'space-between',
        alignItems: 'center',
        gap: '8px',
        flexWrap: 'wrap',
    },
    subText: {
        color: tokens.colorNeutralForeground3,
    },
    errorText: {
        color: tokens.colorPaletteRedForeground1,
    },
    emptyState: {
        display: 'flex',
        flexDirection: 'column',
        gap: '12px',
        alignItems: 'flex-start',
        padding: '12px 0',
    },
    assignRow: {
        display: 'flex',
        gap: '8px',
        alignItems: 'flex-end',
        flexWrap: 'wrap',
    },
    grow: {
        flex: '1',
        minWidth: '220px',
    },
    fieldList: {
        display: 'flex',
        flexDirection: 'column',
        gap: '14px',
    },
    readOnlyRow: {
        display: 'flex',
        flexDirection: 'column',
        gap: '2px',
        borderBottom: `1px solid ${tokens.colorNeutralStroke2}`,
        paddingBottom: '8px',
    },
    label: {
        fontWeight: tokens.fontWeightSemibold,
    },
    buttonRow: {
        display: 'flex',
        gap: '8px',
        justifyContent: 'flex-end',
        flexWrap: 'wrap',
    },
    schemaBadgeRow: {
        display: 'flex',
        gap: '8px',
        alignItems: 'center',
        flexWrap: 'wrap',
    },
});
