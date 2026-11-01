import {makeStyles} from '@fluentui/react-components';

export const useMyGroupsTableStyles = makeStyles({
    table: {
        width: '100%',
        overflowX: 'auto',
        minWidth: 0,
    },
    statusCell: {
        width: '90px',
        minWidth: '90px',
        maxWidth: '90px',
    },
    actionsCell: {
        width: '60px',
        minWidth: '60px',
        maxWidth: '60px',
    },
});
