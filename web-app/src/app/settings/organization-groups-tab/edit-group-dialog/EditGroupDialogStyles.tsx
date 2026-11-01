import {tokens, makeStyles} from "@fluentui/react-components";

export const useEditGroupDialogStyles = makeStyles({
    dialogTitleContainer: {
        display: "flex",
        flexDirection: "row",
        justifyContent: "space-between",
        alignItems: "center",
        flexWrap: "wrap",
        gap: tokens.spacingHorizontalS,
    },
    dialogContentContainer: {
        margin: `${tokens.spacingVerticalS} 0`,
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingHorizontalL,
        minHeight: "520px",
        overflowX: "auto",
        "@media (max-width: 768px)": {
            minHeight: "auto",
        },
    },
});
