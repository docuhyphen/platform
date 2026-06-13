import {makeStyles, tokens} from "@fluentui/react-components";
import {SETTINGS_HEADER_HEIGHT} from "../SettingsStyles.tsx";

export const useWorkflowsTabStyles = makeStyles({
    container: {
        display: "flex",
        flexDirection: "column",
        gap: "8px",
    },

    tabListWrapper: {
        position: "sticky",
        top: SETTINGS_HEADER_HEIGHT,
        zIndex: 1,
        background: tokens.colorNeutralBackground1,
        paddingBottom: "4px",
        marginBottom: "4px",
    },

    content: {
        flex: 1,
    },
});

