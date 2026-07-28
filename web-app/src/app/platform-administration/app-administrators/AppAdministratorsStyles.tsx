import {makeStyles, shorthands, tokens} from '@fluentui/react-components';

export const useAppAdministratorsStyles = makeStyles({
    container: {
        display: 'flex',
        flexDirection: 'column',
        ...shorthands.gap(tokens.spacingHorizontalM),
    },
    header: {
        display: 'flex',
        justifyContent: 'flex-end',
        alignItems: 'center',
    },
    loading: {
        display: 'flex',
        justifyContent: 'center',
        ...shorthands.padding(tokens.spacingHorizontalXXXL),
    },
});
