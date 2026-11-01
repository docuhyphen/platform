import {makeStyles, tokens} from "@fluentui/react-components";

export const useWorkflowGraphDirectionToggleStyles = makeStyles({
    directionToggle: {
        position: "absolute",
        right: tokens.spacingHorizontalM,
        bottom: tokens.spacingVerticalM,
        zIndex: 2,
    },
    directionToggleButton: {
        boxShadow: tokens.shadow4,
    },
    directionToggleIcon: {
        fontSize: tokens.fontSizeBase500,
    },
});
