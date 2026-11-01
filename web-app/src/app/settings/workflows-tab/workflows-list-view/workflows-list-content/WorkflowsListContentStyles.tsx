import {makeStyles, tokens} from "@fluentui/react-components";

export const useWorkflowsListContentStyles = makeStyles({
    scrollableContent: {
        flex: 1,
        minHeight: 0,
        overflowY: "auto",
        overflowX: "hidden",
        overscrollBehavior: "contain",
        paddingInline: tokens.spacingHorizontalS,
        boxSizing: "border-box",
    },
    errorBar: {
        marginBottom: tokens.spacingVerticalM,
    },
    emptyState: {
        padding: tokens.spacingHorizontalXXL,
        color: tokens.colorNeutralForeground3,
        textAlign: "center",
        border: `1px dashed ${tokens.colorNeutralStroke2}`,
        borderRadius: tokens.borderRadiusMedium,
    },
});
