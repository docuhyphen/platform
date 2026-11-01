import {makeStyles, shorthands, tokens} from "@fluentui/react-components";

export const useSessionsTableStyles = makeStyles({
    tableWrapper: {
        width: "100%",
        overflowX: "auto",
        ...shorthands.border("1px", "solid", tokens.colorNeutralStroke2),
        ...shorthands.borderRadius(tokens.borderRadiusXLarge),
    },

    table: {
        minWidth: "760px",
        width: "100%",
        backgroundColor: tokens.colorNeutralBackground1,
    },

    deviceColumn: {
        minWidth: "180px",
    },

    ipColumn: {
        minWidth: "110px",
    },

    dateColumn: {
        minWidth: "125px",
    },

    statusColumn: {
        minWidth: "110px",
    },

    actionsColumn: {
        minWidth: "120px",
        textAlign: "right",
    },
});
