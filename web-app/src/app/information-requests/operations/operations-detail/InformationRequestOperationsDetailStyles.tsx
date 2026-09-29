import {makeStyles, tokens} from "@fluentui/react-components";

export const useInformationRequestOperationsDetailStyles = makeStyles({
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
        flexWrap: "wrap",
        gap: tokens.spacingHorizontalS,
        minWidth: 0,
        maxWidth: "1100px",
    },
    title: {
        flexGrow: 1,
    },
    note: {
        color: tokens.colorNeutralForeground2,
    },
    content: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingVerticalM,
        maxWidth: "1100px",
        minWidth: 0,
    },
    tabs: {
        maxWidth: "100%",
        overflowX: "auto",
        flexShrink: 0,
    },
});
