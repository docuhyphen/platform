import {makeStyles, tokens} from "@fluentui/react-components";

export const useInformationRequestOperationsStyles = makeStyles({
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
    header: {
        display: "flex",
        alignItems: "center",
        justifyContent: "space-between",
        gap: tokens.spacingHorizontalM,
        flexWrap: "wrap",
        maxWidth: "1100px",
    },
    tabs: {
        overflowX: "auto",
        maxWidth: "100%",
    },
});
