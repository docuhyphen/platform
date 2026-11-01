import {makeStyles, tokens} from "@fluentui/react-components";

export const useVariableTokenPreviewStyles = makeStyles({
    preview: {
        padding: `${tokens.spacingVerticalSNudge} ${tokens.spacingHorizontalS}`,
        background: tokens.colorNeutralBackground2,
        borderRadius: tokens.borderRadiusMedium,
        fontSize: tokens.fontSizeBase200,
        lineHeight: "1.8",
        wordBreak: "break-word",
    },
});
