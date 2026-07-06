import {makeStyles, tokens} from "@fluentui/react-components";

export const useOrganizationGroupsTableStyles = makeStyles({
    table: {
        width: "100%",
        minWidth: "600px"
    },
    tableHeader: {
        position: "sticky",
        top: 0,
        zIndex: 1,
        backgroundColor: tokens.colorNeutralBackground1
    },
    groupName: {
        display: "block",
        maxWidth: "320px",
        overflow: "hidden",
        textOverflow: "ellipsis",
        whiteSpace: "nowrap"
    },
    statusCell: {
        width: "90px",
        minWidth: "90px",
        maxWidth: "90px"
    },
    actionsCell: {
        width: "60px",
        minWidth: "60px",
        maxWidth: "60px"
    }
});
