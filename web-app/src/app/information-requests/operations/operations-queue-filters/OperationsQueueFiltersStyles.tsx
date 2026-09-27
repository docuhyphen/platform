import {makeStyles, tokens} from "@fluentui/react-components";

export const useOperationsQueueFiltersStyles = makeStyles({
    filters: {
        display: "flex",
        alignItems: "flex-end",
        gap: tokens.spacingHorizontalL,
        flexWrap: "wrap",
    },
    field: {
        minWidth: "200px",
        "@media (max-width: 640px)": {
            width: "100%",
        },
    },
});
