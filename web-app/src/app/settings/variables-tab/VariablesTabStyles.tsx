import {makeStyles, tokens} from '@fluentui/react-components';

export const useVariablesTabStyles = makeStyles({
    container: {
        display: 'flex',
        flexDirection: 'column',
        gap: '16px',
        width: '100%',
    },
    header: {
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
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
    codeCell: {
        fontFamily: 'monospace',
        fontSize: '12px',
    },
    toolbar: {
        display: 'flex',
        justifyContent: 'flex-end',
    },
});
