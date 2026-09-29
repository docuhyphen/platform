import {makeStyles, tokens} from "@fluentui/react-components";

export const useAuditSearchFiltersStyles = makeStyles({
    filters: {
        display: "grid",
        gridTemplateColumns: "repeat(auto-fit, minmax(200px, 1fr))",
        gap: tokens.spacingHorizontalM,
        alignItems: "end",
    },
    actions: {
        display: "flex",
        justifyContent: "flex-end",
    },
});
