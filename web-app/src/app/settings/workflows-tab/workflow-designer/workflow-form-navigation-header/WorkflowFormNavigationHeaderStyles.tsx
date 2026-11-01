import {makeStyles, tokens} from "@fluentui/react-components";

export const useWorkflowFormNavigationHeaderStyles = makeStyles({
    formNavigationHeader: {
        position: "sticky",
        top: 0,
        zIndex: 2,
        display: "flex",
        alignItems: "center",
        gap: tokens.spacingHorizontalS,
        paddingBlock: tokens.spacingVerticalS,
        backgroundColor: tokens.colorNeutralBackground2,
        border: `1px solid ${tokens.colorNeutralStroke2}`,
        borderRadius: tokens.borderRadiusMedium,
        paddingInline: tokens.spacingHorizontalS,
        flexShrink: 0,
    },
});
