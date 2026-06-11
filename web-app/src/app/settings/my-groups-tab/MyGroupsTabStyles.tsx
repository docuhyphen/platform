import {makeStyles, shorthands, tokens} from '@fluentui/react-components';

export const useMyGroupsTabStyles = makeStyles({
    container: {
        display: 'flex',
        flexDirection: 'column',
        ...shorthands.gap('12px'),
    },
    header: {
        display: 'flex',
        justifyContent: 'space-between',
        alignItems: 'center',
    },
    table: {
        width: '100%',
    },
    loading: {
        display: 'flex',
        justifyContent: 'center',
        ...shorthands.padding('32px'),
    },
    error: {
        color: tokens.colorPaletteRedForeground1,
        ...shorthands.padding('8px'),
    },
});
