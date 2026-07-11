import {makeStyles, tokens} from "@fluentui/react-components";
import {SETTINGS_HEADER_HEIGHT} from "../SettingsStyles.tsx";

export const useOrganizationTabStyles = makeStyles({
    orgOnboardingContainer: {
        display: "flex",
        flexDirection: "column",
        width: "100%",
        maxWidth: "30rem",
        minHeight: `calc(100vh - ${SETTINGS_HEADER_HEIGHT} - 8rem)`,
        gap: tokens.spacingHorizontalL,
        alignItems: "center",
        justifyContent: "center",
        textAlign: "center",
        margin: "0 auto",
        padding: tokens.spacingHorizontalL,
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
        gap: tokens.spacingHorizontalS,
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
        paddingBottom: tokens.spacingVerticalXS,
        marginBottom: tokens.spacingVerticalXS,
        flexShrink: 0,
        paddingInline: tokens.spacingHorizontalS,
        boxSizing: "border-box",
        // borderBottom: `1px solid ${tokens.colorNeutralStroke2}`,
    },

    dataContainer: {
        display: "flex",
        flexDirection: "row",
        gap: tokens.spacingHorizontalXXL
    },

    dataName: {
        minWidth: "200px"
    },

    dataEditable: {
        display: "flex",
        gap: tokens.spacingHorizontalS
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
        paddingInline: tokens.spacingHorizontalS,
        boxSizing: "border-box",
    },
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
    onboardingRow: {
        marginTop: tokens.spacingVerticalS,
    },
});
