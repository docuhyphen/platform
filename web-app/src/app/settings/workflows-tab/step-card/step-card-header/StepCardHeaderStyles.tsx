import {makeStyles, tokens} from "@fluentui/react-components";

export const useStepCardHeaderStyles = makeStyles({
    cardHeader: {
        display: "flex",
        alignItems: "center",
        justifyContent: "space-between",
        gap: tokens.spacingHorizontalS,
        padding: `${tokens.spacingVerticalMNudge} ${tokens.spacingHorizontalL}`,
        background: tokens.colorNeutralBackground2,
        position: "sticky",
        top: 0,
        zIndex: 2,
        border: `1px solid ${tokens.colorNeutralStroke2}`,
        borderRadius: tokens.borderRadiusMedium,
    },
});
