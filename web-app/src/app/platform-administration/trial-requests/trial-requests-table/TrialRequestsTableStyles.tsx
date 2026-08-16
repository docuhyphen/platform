import {makeStyles, shorthands, tokens} from "@fluentui/react-components";

export const useTrialRequestsTableStyles = makeStyles({
    scroll: {overflowX: "auto"},
    table: {minWidth: "800px"},
    actions: {textAlign: "right"},
    actionButtons: {
        display: "flex",
        justifyContent: "flex-end",
        flexWrap: "wrap",
        ...shorthands.gap(tokens.spacingHorizontalXS),
    },
});
