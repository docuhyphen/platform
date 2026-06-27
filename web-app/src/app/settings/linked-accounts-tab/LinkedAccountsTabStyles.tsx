import {makeStyles, shorthands} from '@fluentui/react-components';

export const useLinkedAccountsTabStyles = makeStyles({
    container: {
        display: 'flex',
        flexDirection: 'column',
        ...shorthands.gap('16px'),
        maxWidth: '500px',
    },
});
