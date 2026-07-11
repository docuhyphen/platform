import {makeStyles, tokens} from '@fluentui/react-components';

export const useOrganizationVariablesTabStyles = makeStyles({
    container: {
        display: 'flex',
        flexDirection: 'column',
        gap: tokens.spacingHorizontalM,
        padding: `0 ${tokens.spacingHorizontalXS}`,
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
    variableRow: {
        border: `1px solid ${tokens.colorNeutralStroke1}`,
        borderRadius: tokens.borderRadiusXLarge,
        padding: `${tokens.spacingVerticalMNudge} ${tokens.spacingHorizontalL}`,
        display: 'flex',
        justifyContent: 'space-between',
        alignItems: 'center',
        gap: tokens.spacingHorizontalS,
    },
    variableRowInner: {
        display: 'flex',
        gap: tokens.spacingHorizontalM,
        alignItems: 'center',
        flex: '1',
        minWidth: '0',
    },
    actionGroup: {
        display: 'flex',
        alignItems: 'center',
        gap: tokens.spacingHorizontalXS,
        flexShrink: 0,
    },
    copySuccess: {
        color: tokens.colorPaletteGreenForeground1,
    },
    codeKey: {
        fontFamily: 'monospace',
        fontWeight: '600',
    },
    tokenCell: {
        display: 'flex',
        alignItems: 'center',
        gap: tokens.spacingHorizontalS,
    },
    defaultValueText: {
        color: tokens.colorNeutralForeground2,
    },
    drawerBody: {
        display: 'flex',
        flexDirection: 'column',
        gap: tokens.spacingHorizontalL,
        paddingTop: tokens.spacingVerticalL,
    },
    buttonRow: {
        display: 'flex',
        gap: tokens.spacingHorizontalS,
        justifyContent: 'flex-end',
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
