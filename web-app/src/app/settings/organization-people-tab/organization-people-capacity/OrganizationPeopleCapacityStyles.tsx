import {makeStyles, tokens} from "@fluentui/react-components";

export const useOrganizationPeopleCapacityStyles = makeStyles({
    container: {
        padding: `${tokens.spacingVerticalM} ${tokens.spacingHorizontalL}`,
        borderRadius: tokens.borderRadiusXLarge,
        border: `1px solid ${tokens.colorNeutralStroke2}`,
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingHorizontalSNudge,
        flexShrink: 0
    },
    row: {
        display: "flex",
        justifyContent: "space-between",
        alignItems: "center",
        gap: tokens.spacingHorizontalM,
        "@media (max-width: 600px)": {
            alignItems: "flex-start",
            flexDirection: "column"
        }
    },
    atCap: {
        color: tokens.colorPaletteRedForeground1
    },
    nearCap: {
        color: tokens.colorPaletteYellowForeground1
    }
});
