import {makeStyles, tokens} from "@fluentui/react-components";
import {SETTINGS_HEADER_HEIGHT} from "../SettingsStyles.tsx";

export const useOrganizationTabStyles = makeStyles({
    orgOnboardingContainer: {
        display: "flex",
        flexDirection: "column",
        width: "400px",
        gap: "16px"
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
        borderBottom: `1px solid ${tokens.colorNeutralStroke2}`,
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
        // Natural height — content scrolls via the settings container scroller.
    }
});