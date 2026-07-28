import {makeStyles, tokens} from "@fluentui/react-components";

export const useAppAdministratorsTableStyles = makeStyles({
    scrollContainer: {
        width: "100%",
        overflowX: "auto",
    },
    table: {
        width: "100%",
        minWidth: "640px",
    },
    actionsCell: {
        width: "90px",
        minWidth: "90px",
        maxWidth: "90px",
        color: tokens.colorNeutralForeground1,
    },
});
