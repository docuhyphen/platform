// OrganizationPeopleTabStyles.tsx
import {makeStyles, shorthands} from "@fluentui/react-components";

export const useOrganizationPeopleTabStyles = makeStyles({
    container: {
        display: 'flex',
        flexDirection: 'column',
        gap: '20px',
        ...shorthands.padding('20px'),
        width: '100%'
    },
    header: {
        display: 'flex',
        justifyContent: 'space-between',
        alignItems: 'center'
    },
    searchBox: {
      flexGrow: 1
    },
    table: {
        width: '100%'
    },
    loading: {
        display: 'flex',
        justifyContent: 'center',
        ...shorthands.padding('40px')
    },
    error: {
        color: 'red',
        ...shorthands.padding('10px'),
        backgroundColor: '#ffeeee',
        ...shorthands.borderRadius('4px')
    },
    actions: {
        display: 'flex',
        gap: '8px'
    }
});