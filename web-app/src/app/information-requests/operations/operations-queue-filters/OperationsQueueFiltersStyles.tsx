import {makeStyles, tokens} from "@fluentui/react-components";

export const useOperationsQueueFiltersStyles = makeStyles({
    filters: {
        display: "flex",
        alignItems: "flex-end",
        gap: tokens.spacingHorizontalL,
        flexWrap: "wrap",
    },
    search: {
        flexGrow: 1,
        minWidth: "240px",
        "@media (max-width: 640px)": {
            width: "100%",
        },
    },
    field: {
        minWidth: "200px",
        "@media (max-width: 640px)": {
            width: "100%",
        },
    },
});
