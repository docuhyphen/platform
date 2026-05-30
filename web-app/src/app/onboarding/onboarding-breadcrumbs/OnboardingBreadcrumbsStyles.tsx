import {makeStyles, tokens} from "@fluentui/react-components";

export const useOnboardingBreadcrumbsStyles = makeStyles({

    onBoardingBreadcrumbs: {
        display: "flex",
        gap: "16px",
        flexDirection: "column",
        alignItems: "start",
    },

    onBoardingBreadcrumbItem: {

        display: "flex",
        alignItems: "center",
        width: "100%",
        gap: "16px",
        position: "relative",

        "&:before": {
            content: "''",
            display: "block",
            width: "2px",
            height: "100%",
            background: tokens.colorNeutralForegroundOnBrand,
            opacity: 0.3,
            position: "absolute",
            left: "8px",
            top: "27px",
        },

        "&:last-child:before": {
            display: "none",
        },
    },

    onBoardingBreadcrumbItemIcon: {
        fontSize: "18px",
        position: "relative"
    },

    onBoardingBreadcrumbItemText: {
        flex: 1,
        background: tokens.colorNeutralBackgroundAlpha,
        padding: "8px 16px",
        borderRadius: "4px",
        cursor: "default",
    },

    onBoardingBreadcrumbItemDisabled: {
        opacity: "0.4",
    },

    onBoardingBreadcrumbItemCurrent: {
        border: `1px solid ${tokens.colorNeutralForegroundOnBrand}`,
        boxShadow: tokens.shadow8,
    }
});