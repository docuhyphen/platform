import {makeStyles, tokens} from "@fluentui/react-components";

export const useInformationRequestReviewWorkspaceStyles = makeStyles({
    page: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingVerticalL,
        padding: tokens.spacingHorizontalXXL,
        boxSizing: "border-box",
        minWidth: 0,
        height: "100%",
        overflowY: "auto",
        "@media (max-width: 640px)": {
            padding: tokens.spacingHorizontalM,
        },
    },
});
