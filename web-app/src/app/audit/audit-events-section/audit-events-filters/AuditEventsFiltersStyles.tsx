import {makeStyles, tokens} from "@fluentui/react-components";

export const useAuditEventsFiltersStyles = makeStyles({
    filterPanel: {
        display: "grid",
        gridTemplateColumns: "minmax(0, 2fr) minmax(0, 1fr) minmax(0, 1fr)",
        gap: tokens.spacingHorizontalM,
        "@media (max-width: 768px)": {
            gridTemplateColumns: "1fr",
        },
    },
    filterField: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingVerticalXS,
        minWidth: 0,
    },
    selectedCategories: {
        display: "flex",
        flexWrap: "wrap",
        gap: tokens.spacingHorizontalXS,
        minWidth: 0,
    },
});
