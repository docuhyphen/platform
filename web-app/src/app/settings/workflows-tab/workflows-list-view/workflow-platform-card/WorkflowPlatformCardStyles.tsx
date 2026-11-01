import {makeStyles, tokens} from "@fluentui/react-components";

export const useWorkflowPlatformCardStyles = makeStyles({
    templateCard: {
        padding: tokens.spacingHorizontalL,
        border: `1px solid ${tokens.colorNeutralStroke2}`,
        borderRadius: tokens.borderRadiusMedium,
        display: "flex",
        alignItems: "flex-start",
        gap: tokens.spacingHorizontalL,
    },
    templateCardInfo: {
        flex: 1,
        minWidth: 0,
    },
    platformCardTriggerText: {
        marginTop: tokens.spacingVerticalXXS,
    },
});
