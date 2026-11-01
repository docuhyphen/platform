import {makeStyles, tokens} from "@fluentui/react-components";

export const useExchangeDetailSectionStyles = makeStyles({
    sectionCard: {
        border: `1px solid ${tokens.colorNeutralStroke2}`,
        borderRadius: tokens.borderRadiusXLarge,
        padding: tokens.spacingHorizontalM,
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingHorizontalS,
    },
});
