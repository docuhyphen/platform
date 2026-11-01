import {makeStyles, tokens} from "@fluentui/react-components";

export const useSecuritySessionPolicySectionStyles = makeStyles({
    divider: {
        marginTop: tokens.spacingVerticalXL,
        marginBottom: tokens.spacingVerticalM,
    },
    loadingWrapper: {
        padding: tokens.spacingHorizontalS,
    },
    error: {
        color: tokens.colorStatusDangerForeground1,
        marginBottom: tokens.spacingVerticalS,
    },
    noIdpText: {
        color: tokens.colorNeutralForeground3,
    },
});
