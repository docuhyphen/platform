import {makeStyles, tokens} from "@fluentui/react-components";

export const useNoAuthExchangeDocumentListStyles = makeStyles({

    container: {
        display: "flex",
        flexDirection: "column",
        justifyContent: "space-between",
        gap: tokens.spacingHorizontalM,
    },

    accessWindowHint: {
        color: tokens.colorNeutralForeground2,
        padding: `${tokens.spacingVerticalMNudge} ${tokens.spacingHorizontalM}`,
        background: tokens.colorNeutralBackground2,
        border: `1px solid ${tokens.colorNeutralStroke2}`,
        borderRadius: tokens.borderRadiusLarge,
        lineHeight: "1.5",
    },

});
