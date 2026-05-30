import {makeStyles, shorthands, tokens} from "@fluentui/react-components";

export const useOrganizationPeopleTabStyles = makeStyles({
    container: {
        display: 'flex',
        flexDirection: 'column',
        gap: '20px',
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
    },
    error: {
        color: tokens.colorStatusDangerForeground1,
        ...shorthands.padding('10px'),
        backgroundColor: tokens.colorStatusDangerBackground1,
        ...shorthands.borderRadius('4px')
    },
    actions: {
        display: 'flex',
        gap: '8px'
    },

    dataContainer: {
        display: "flex",
        flexDirection: "row",
        gap: "16px"
    },

    dataEditable: {
        display: "flex",
        gap: "8px"
    },
    truncateCell: {
        maxWidth: "260px",
        overflow: "hidden",
        textOverflow: "ellipsis",
        whiteSpace: "nowrap"
    }
});