import {makeStyles, tokens} from "@fluentui/react-components";

export const useWorkflowViewSwitchStyles = makeStyles({
    switchBar: {
        display: "flex",
        alignItems: "center",
        justifyContent: "flex-end",
        width: "100%",
    },
    toggle: {
        flexShrink: 0,
    },
    icon: {
        color: tokens.colorNeutralForeground2,
    },
});
