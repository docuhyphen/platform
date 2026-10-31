import {makeStyles, tokens} from "@fluentui/react-components";

export const useStructuredResponseToolbarStyles = makeStyles({
    toolbar: {
        display: "flex",
        justifyContent: "space-between",
        alignItems: "center",
        gap: tokens.spacingHorizontalM,
        flexWrap: "wrap",
    },
    groupControls: {
        display: "flex",
        gap: tokens.spacingHorizontalS,
        flexWrap: "wrap",
    },
});
