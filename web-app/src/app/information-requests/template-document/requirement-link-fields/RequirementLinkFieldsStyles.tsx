import {makeStyles, tokens} from "@fluentui/react-components";

export const useRequirementLinkFieldsStyles = makeStyles({
    stack: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingVerticalM,
    },
    muted: {
        color: tokens.colorNeutralForeground3,
    },
});
