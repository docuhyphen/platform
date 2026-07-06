import {makeStyles, tokens} from "@fluentui/react-components";

export const useOrganizationPeopleCapacityStyles = makeStyles({
    container: {
        padding: "12px 16px",
        borderRadius: tokens.borderRadiusXLarge,
        border: `1px solid ${tokens.colorNeutralStroke2}`,
        display: "flex",
        flexDirection: "column",
        gap: "6px",
        flexShrink: 0
    },
    row: {
        display: "flex",
        justifyContent: "space-between",
        alignItems: "center",
        gap: "12px",
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
