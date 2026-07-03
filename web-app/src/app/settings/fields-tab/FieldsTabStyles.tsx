import {makeStyles, tokens} from '@fluentui/react-components';

export const useFieldsTabStyles = makeStyles({
    container: {
        display: 'flex',
        flexDirection: 'column',
        gap: '12px',
        padding: '0 4px',
    },
    headerRow: {
        display: 'flex',
        justifyContent: 'space-between',
        alignItems: 'center',
        gap: '8px',
        flexWrap: 'wrap',
    },
    descriptionText: {
        color: tokens.colorNeutralForeground3,
    },
    errorText: {
        color: tokens.colorPaletteRedForeground1,
    },
    emptyText: {
        color: tokens.colorNeutralForeground3,
    },
    list: {
        display: 'flex',
        flexDirection: 'column',
        gap: '8px',
    },
    card: {
        border: `1px solid ${tokens.colorNeutralStroke1}`,
        borderRadius: tokens.borderRadiusXLarge,
        padding: '10px 16px',
        display: 'flex',
        justifyContent: 'space-between',
        alignItems: 'center',
        gap: '8px',
        flexWrap: 'wrap',
    },
    cardMain: {
        display: 'flex',
        flexDirection: 'column',
        gap: '2px',
        minWidth: '0',
        flex: '1',
    },
    cardTitleRow: {
        display: 'flex',
        alignItems: 'center',
        gap: '8px',
        flexWrap: 'wrap',
    },
    codeKey: {
        fontFamily: 'monospace',
        fontWeight: '600',
    },
    subText: {
        color: tokens.colorNeutralForeground3,
    },
    actionGroup: {
        display: 'flex',
        alignItems: 'center',
        gap: '4px',
        flexShrink: 0,
    },
    badgeRow: {
        display: 'flex',
        gap: '6px',
        alignItems: 'center',
        flexWrap: 'wrap',
    },
    drawerBody: {
        display: 'flex',
        flexDirection: 'column',
        gap: '16px',
        paddingTop: '16px',
    },
    fieldGroup: {
        display: 'flex',
        flexDirection: 'column',
        gap: '12px',
    },
    twoColumn: {
        display: 'flex',
        gap: '12px',
        flexWrap: 'wrap',
    },
    grow: {
        flex: '1',
        minWidth: '160px',
    },
    optionRow: {
        display: 'flex',
        gap: '8px',
        alignItems: 'flex-end',
    },
    bindingRow: {
        border: `1px solid ${tokens.colorNeutralStroke2}`,
        borderRadius: tokens.borderRadiusMedium,
        padding: '10px 12px',
        display: 'flex',
        flexDirection: 'column',
        gap: '8px',
    },
    bindingHeader: {
        display: 'flex',
        justifyContent: 'space-between',
        alignItems: 'center',
        gap: '8px',
    },
    bindingToggles: {
        display: 'flex',
        gap: '16px',
        alignItems: 'center',
        flexWrap: 'wrap',
    },
    buttonRow: {
        display: 'flex',
        gap: '8px',
        justifyContent: 'flex-end',
        flexWrap: 'wrap',
    },
    inlineSpinner: {
        paddingTop: '8px',
    },
});
