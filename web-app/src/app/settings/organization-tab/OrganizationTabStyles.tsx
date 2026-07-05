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
        height: "100%",
        minHeight: 0,
    },

    /**
     * Sticky wrapper for the inner TabList (Details / People / Groups / …).
     * The Settings content pane is the scroll container, so the sub-navigation
     * stays at its top while the selected section scrolls beneath it.
     */
    tabListWrapper: {
        background: tokens.colorNeutralBackground1,
        paddingBottom: "4px",
        marginBottom: "4px",
        flexShrink: 0,
        paddingInline: "0.5rem",
        boxSizing: "border-box",
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
        flex: 1,
        minHeight: 0,
        overflowY: "auto",
        overflowX: "hidden",
        overscrollBehavior: "contain",
        paddingInline: "0.5rem",
        boxSizing: "border-box",
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
