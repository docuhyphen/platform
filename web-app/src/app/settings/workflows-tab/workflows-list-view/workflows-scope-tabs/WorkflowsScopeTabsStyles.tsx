import {makeStyles, tokens} from "@fluentui/react-components";

export const useWorkflowsScopeTabsStyles = makeStyles({
    outerWrapper: {
        display: "flex",
        alignItems: "center",
        justifyContent: "space-between",
        background: tokens.colorNeutralBackground1,
        paddingBottom: tokens.spacingVerticalM,
        gap: tokens.spacingHorizontalS,
        flexWrap: "wrap",
        flexShrink: 0,
        paddingInline: tokens.spacingHorizontalS,
        boxSizing: "border-box",
    },
});
