import {makeStyles, tokens} from "@fluentui/react-components";

export const useDocumentsTabStyles = makeStyles({
    container: {
        display: "flex",
        flexDirection: "column",
        gap: 0,
        width: "100%",
        height: "100%",
        minHeight: 0,
    },
    stickyBlock: {
        background: tokens.colorNeutralBackground1,
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingHorizontalS,
        paddingBottom: tokens.spacingVerticalS,
        flexShrink: 0,
        paddingInline: tokens.spacingHorizontalS,
        boxSizing: "border-box",
    },
});
