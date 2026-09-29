import {makeStyles, tokens} from "@fluentui/react-components";

export const useAuthorWorkspaceHeaderStyles = makeStyles({
    header: {
        display: "flex",
        alignItems: "flex-start",
        justifyContent: "space-between",
        flexWrap: "wrap",
        gap: tokens.spacingHorizontalM,
    },
    titleGroup: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingVerticalXS,
        minWidth: 0,
    },
    titleLine: {
        display: "flex",
        alignItems: "center",
        flexWrap: "wrap",
        gap: tokens.spacingHorizontalS,
    },
    title: {
        overflowWrap: "anywhere",
    },
    actions: {
        display: "flex",
        flexWrap: "wrap",
        gap: tokens.spacingHorizontalS,
    },
});
