import {makeStyles, tokens} from '@fluentui/react-components';

export const useVariableCardStyles = makeStyles({
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
    codeKey: {
        fontFamily: 'monospace',
        fontWeight: tokens.fontWeightSemibold,
    },
    defaultValueText: {
        color: tokens.colorNeutralForeground2,
    },
});
