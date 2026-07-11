import {makeStyles, tokens} from "@fluentui/react-components";

export const useWorkflowDesignerActionBarStyles = makeStyles({
    saveBar: {
        position: "sticky",
        bottom: 0,
        zIndex: 3,
        display: "flex",
        alignItems: "center",
        justifyContent: "flex-end",
        gap: tokens.spacingHorizontalS,
        paddingTop: tokens.spacingVerticalM,
        paddingBottom: tokens.spacingVerticalS,
        paddingInline: tokens.spacingHorizontalM,
        backgroundColor: tokens.colorNeutralBackground1,
        borderTop: `1px solid ${tokens.colorNeutralStroke2}`,
        flexShrink: 0,
    },
});
