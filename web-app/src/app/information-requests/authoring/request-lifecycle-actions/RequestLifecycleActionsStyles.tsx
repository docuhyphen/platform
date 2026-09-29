import {makeStyles, tokens} from "@fluentui/react-components";

export const useRequestLifecycleActionsStyles = makeStyles({
    actions: {
        display: "flex",
        alignItems: "center",
        flexWrap: "wrap",
        gap: tokens.spacingHorizontalS,
    },
    muted: {
        color: tokens.colorNeutralForeground2,
    },
});
