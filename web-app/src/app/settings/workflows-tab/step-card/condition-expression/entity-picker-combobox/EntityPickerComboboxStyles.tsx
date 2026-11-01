import {makeStyles, tokens} from "@fluentui/react-components";
import {conditionValueControlStyle} from "../conditionValueControlStyleFragments.ts";

export const useEntityPickerComboboxStyles = makeStyles({
    valueControl: conditionValueControlStyle,
    sublabel: {
        color: tokens.colorNeutralForeground3,
        marginLeft: tokens.spacingHorizontalS,
        fontSize: tokens.fontSizeBase200,
    },
});
