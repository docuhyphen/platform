import {makeStyles, tokens} from "@fluentui/react-components";

export const useCommunicationsScopeTabsStyles = makeStyles({
    headerRow: {
        display: "flex",
        alignItems: "center",
        justifyContent: "space-between",
        background: tokens.colorNeutralBackground1,
        paddingBottom: tokens.spacingVerticalS,
        flexWrap: "wrap",
        gap: tokens.spacingHorizontalS,
        paddingInline: tokens.spacingHorizontalS,
        boxSizing: "border-box",
    },
});
