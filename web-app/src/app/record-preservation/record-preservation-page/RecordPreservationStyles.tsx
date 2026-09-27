import {makeStyles, tokens} from "@fluentui/react-components";

export const useRecordPreservationStyles = makeStyles({
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
        gap: tokens.spacingHorizontalS,
        minWidth: 0,
    },
    content: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingVerticalL,
        maxWidth: "1100px",
        minWidth: 0,
    },
});
