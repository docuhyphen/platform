import {makeStyles, tokens} from '@fluentui/react-components';

export const useVariablesTabStyles = makeStyles({
    container: {
        display: 'flex',
        flexDirection: 'column',
        gap: tokens.spacingHorizontalS,
        width: '100%',
        height: '100%',
        minHeight: 0,
    },
    header: {
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
        background: tokens.colorNeutralBackground1,
        paddingBottom: tokens.spacingVerticalS,
        flexWrap: 'wrap',
        gap: tokens.spacingHorizontalS,
        paddingInline: tokens.spacingHorizontalS,
        boxSizing: 'border-box',
    },
    scrollableContent: {
        flex: 1,
        minHeight: 0,
        overflowY: 'auto',
        overflowX: 'hidden',
        overscrollBehavior: 'contain',
        paddingInline: tokens.spacingHorizontalS,
        boxSizing: 'border-box',
        display: 'flex',
        flexDirection: 'column',
        gap: tokens.spacingHorizontalL,
    },
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
    copySuccess: {
        color: tokens.colorPaletteGreenForeground1,
    },
    codeCell: {
        fontFamily: 'monospace',
        fontSize: '12px',
    },
    tokenCell: {
        display: 'flex',
        alignItems: 'center',
        gap: tokens.spacingHorizontalS,
    },
    toolbar: {
        display: 'flex',
        justifyContent: 'flex-end',
    },
});
