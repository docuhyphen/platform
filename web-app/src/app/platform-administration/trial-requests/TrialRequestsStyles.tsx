import {makeStyles, shorthands, tokens} from "@fluentui/react-components";

export const useTrialRequestsStyles = makeStyles({
    container: {display: "flex", flexDirection: "column", ...shorthands.gap(tokens.spacingVerticalM)},
    toolbar: {
        display: "flex",
        flexWrap: "wrap",
        alignItems: "end",
        justifyContent: "space-between",
        ...shorthands.gap(tokens.spacingHorizontalM),
    },
    filter: {minWidth: "180px"},
    loading: {display: "flex", justifyContent: "center", ...shorthands.padding(tokens.spacingVerticalL)},
    pagination: {
        display: "flex",
        justifyContent: "flex-end",
        alignItems: "center",
        ...shorthands.gap(tokens.spacingHorizontalS),
    },
});
