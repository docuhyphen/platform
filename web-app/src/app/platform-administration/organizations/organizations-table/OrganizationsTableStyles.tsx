import {makeStyles, shorthands, tokens} from "@fluentui/react-components";

export const useOrganizationsTableStyles = makeStyles({
    scrollContainer: {
        flex: 1,
        width: "100%",
        minWidth: 0,
        minHeight: 0,
        overflow: "auto",
        overscrollBehavior: "contain",
        boxSizing: "border-box",
        ...shorthands.border("1px", "solid", tokens.colorNeutralStroke2),
        ...shorthands.borderRadius(tokens.borderRadiusMedium),
    },
    table: {
        width: "100%",
        minWidth: "64rem",
        tableLayout: "fixed",
    },
    tableHeader: {
        position: "sticky",
        top: 0,
        zIndex: 1,
        backgroundColor: tokens.colorNeutralBackground1,
    },
    actions: {
        textAlign: "right",
    },
    entitlements: {
        maxWidth: "260px",
        whiteSpace: "normal",
    },
});
