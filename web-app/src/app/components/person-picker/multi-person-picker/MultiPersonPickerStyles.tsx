import {makeStyles, tokens} from "@fluentui/react-components";

export const useMultiPersonPickerStyles = makeStyles({
    emptyState: {
        color: tokens.colorNeutralForeground2,
        padding: `${tokens.spacingVerticalS} ${tokens.spacingHorizontalS}`,
    },
});
