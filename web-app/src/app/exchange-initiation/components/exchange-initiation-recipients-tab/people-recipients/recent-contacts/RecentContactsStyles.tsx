import {makeStyles, tokens} from "@fluentui/react-components";

export const useRecentContactsStyles = makeStyles({
    recentContactsRow: {
        display: "flex",
        flexWrap: "wrap",
        gap: tokens.spacingHorizontalS,
    },
    activeRecentContact: {
        "& .fui-Persona__primaryText, & .fui-Persona__secondaryText": {
            color: tokens.colorNeutralForegroundStaticInverted,
        },
    },
});
