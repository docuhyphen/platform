import {makeStyles, tokens} from "@fluentui/react-components";

export const useOrganizationTabStyles = makeStyles({
    loadingWrapper: {
        display: "flex",
        justifyContent: "center",
        padding: tokens.spacingHorizontalXL,
    },
    errorWrapper: {
        color: tokens.colorStatusDangerForeground1,
        padding: tokens.spacingHorizontalMNudge,
        marginBottom: tokens.spacingVerticalMNudge,
    },
});
