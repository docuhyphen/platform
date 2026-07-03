import {makeStyles, tokens} from "@fluentui/react-components";
import {SETTINGS_HEADER_HEIGHT} from "../SettingsStyles.tsx";

export const useOrganizationTabStyles = makeStyles({
    orgOnboardingContainer: {
        display: "flex",
        flexDirection: "column",
        width: "100%",
        maxWidth: "30rem",
        minHeight: `calc(100vh - ${SETTINGS_HEADER_HEIGHT} - 8rem)`,
        gap: "16px",
        alignItems: "center",
        justifyContent: "center",
        textAlign: "center",
        margin: "0 auto",
        padding: "16px",
        boxSizing: "border-box",
    },

    organizationEmptyStateIllustration: {
        width: "100%",
        maxWidth: "520px",
        height: "auto",
        color: tokens.colorBrandForeground1,
        marginBottom: "0",
    },

    container: {
        display: "flex",
        flexDirection: "column",
        gap: "8px",
    },

    /**
     * Sticky wrapper for the inner TabList (Details / People / Groups / …).
     * Sticks just below the fixed app header so the sub-navigation is always
     * visible while the section content scrolls past.
     */
    tabListWrapper: {
        position: "sticky",
        top: SETTINGS_HEADER_HEIGHT,
        zIndex: 1,
        background: tokens.colorNeutralBackground1,
        paddingBottom: "4px",
        marginBottom: "4px",
        // borderBottom: `1px solid ${tokens.colorNeutralStroke2}`,
    },

    dataContainer: {
        display: "flex",
        flexDirection: "row",
        gap: "24px"
    },

    dataName: {
        minWidth: "200px"
    },

    dataEditable: {
        display: "flex",
        gap: "8px"
    },

    mainDivider: {
        width: "300px"
    },
    tabsContainer: {
        // Natural height exch- content scrolls via the settings container scroller.
    },
    loadingWrapper: {
        display: "flex",
        justifyContent: "center",
        padding: "20px",
    },
    errorWrapper: {
        color: tokens.colorStatusDangerForeground1,
        padding: "10px",
        marginBottom: "10px",
    },
    onboardingRow: {
        marginTop: "8px",
    },
});
