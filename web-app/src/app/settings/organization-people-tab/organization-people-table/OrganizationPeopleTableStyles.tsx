import {makeStyles, tokens} from "@fluentui/react-components";

export const useOrganizationPeopleTableStyles = makeStyles({
    table: {
        width: "100%",
        minWidth: "680px"
    },
    tableHeader: {
        position: "sticky",
        top: 0,
        zIndex: 1,
        backgroundColor: tokens.colorNeutralBackground1
    },
    personCell: {
        display: "flex",
        alignItems: "center",
        gap: "8px",
        minWidth: 0
    },
    truncateCell: {
        maxWidth: "260px",
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
