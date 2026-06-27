import {makeStyles, tokens} from '@fluentui/react-components';

export const useAddPersonPanelStyles = makeStyles({
    container: {
        display: 'flex',
        flexDirection: 'column',
        gap: '16px',
    },
    topBar: {
        display: 'flex',
        alignItems: 'center',
        gap: '8px',
    },
    form: {
        display: 'flex',
        flexDirection: 'column',
        gap: '12px',
    },
    row: {
        display: 'flex',
        gap: '8px',
    },
    personField: {
        flex: '1',
    },
    hint: {
        color: tokens.colorNeutralForeground3,
    },
    actions: {
        display: 'flex',
        justifyContent: 'flex-end',
        gap: '8px',
    },
});
