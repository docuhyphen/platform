import {tokens, makeStyles} from "@fluentui/react-components";

export const useRecipientRoleSelectorStyles = makeStyles({
    roleSection: {
        marginTop: tokens.spacingVerticalL,
        paddingTop: tokens.spacingVerticalM,
        borderTop: `1px solid ${tokens.colorNeutralStroke2}`,
    },
    constraintsRow: {
        marginTop: tokens.spacingVerticalS,
    },
    roleInfoRow: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingVerticalXXS,
    },
});
