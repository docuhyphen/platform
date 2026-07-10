import {makeStyles, tokens} from "@fluentui/react-components";

export const useBlueprintsPaginationStyles = makeStyles({
    container: {
        display: "flex",
        alignItems: "center",
        justifyContent: "space-between",
        gap: tokens.spacingHorizontalM,
        width: "100%",
        padding: `${tokens.spacingVerticalS} ${tokens.spacingHorizontalS} 0`,
        boxSizing: "border-box",
        flexShrink: 0,
        "@media (max-width: 600px)": {
            alignItems: "flex-start",
            flexDirection: "column",
        },
    },
    actions: {
        display: "flex",
        justifyContent: "flex-end",
    },
    pageIndicator: {
        display: "flex",
        alignItems: "center",
    },
});
