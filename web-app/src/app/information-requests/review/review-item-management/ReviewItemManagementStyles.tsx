import {makeStyles, tokens} from "@fluentui/react-components";

export const useReviewItemManagementStyles = makeStyles({
    row: {
        display: "flex",
        justifyContent: "space-between",
        alignItems: "flex-start",
        gap: tokens.spacingHorizontalS,
        flexWrap: "wrap",
    },
});
