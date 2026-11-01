import {makeStyles, tokens} from '@fluentui/react-components';

export const usePlatformVariablesViewStyles = makeStyles({
    platformContainer: {
        display: 'flex',
        flexDirection: 'column',
        gap: tokens.spacingHorizontalL,
        padding: `0 ${tokens.spacingHorizontalXS}`,
    },
    systemVarTitle: {
        marginBottom: tokens.spacingVerticalXS,
    },
    systemVarSubtitle: {
        color: tokens.colorNeutralForeground3,
        display: 'block',
        marginBottom: tokens.spacingVerticalM,
    },
    errorText: {
        color: tokens.colorPaletteRedForeground1,
    },
    codeCell: {
        fontFamily: 'monospace',
        fontSize: tokens.fontSizeBase200,
    },
    tokenCell: {
        display: 'flex',
        alignItems: 'center',
        gap: tokens.spacingHorizontalS,
    },
});
