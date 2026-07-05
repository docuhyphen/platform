import {makeStyles, tokens} from '@fluentui/react-components';

export const useVariablesTabStyles = makeStyles({
    container: {
        display: 'flex',
        flexDirection: 'column',
        gap: '8px',
        width: '100%',
        height: '100%',
        minHeight: 0,
    },
    header: {
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
        background: tokens.colorNeutralBackground1,
        paddingBottom: '8px',
        flexWrap: 'wrap',
        gap: '8px',
        paddingInline: '0.5rem',
        boxSizing: 'border-box',
    },
    scrollableContent: {
        flex: 1,
        minHeight: 0,
        overflowY: 'auto',
        overflowX: 'hidden',
        overscrollBehavior: 'contain',
        paddingInline: '0.5rem',
        boxSizing: 'border-box',
        display: 'flex',
        flexDirection: 'column',
        gap: '16px',
    },
    platformContainer: {
        display: 'flex',
        flexDirection: 'column',
        gap: '16px',
        padding: '0 4px',
    },
    systemVarTitle: {
        marginBottom: '4px',
    },
    systemVarSubtitle: {
        color: tokens.colorNeutralForeground3,
        display: 'block',
        marginBottom: '12px',
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
        gap: '8px',
    },
    toolbar: {
        display: 'flex',
        justifyContent: 'flex-end',
    },
});
