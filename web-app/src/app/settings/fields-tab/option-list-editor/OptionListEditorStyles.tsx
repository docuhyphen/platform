import {makeStyles, tokens} from '@fluentui/react-components';
import {fieldsFormStyles} from '../FieldsSharedStyles';

export const useOptionListEditorStyles = makeStyles({
    fieldGroup: fieldsFormStyles.fieldGroup,
    grow: fieldsFormStyles.grow,
    optionRow: {
        display: 'flex',
        gap: tokens.spacingHorizontalS,
        alignItems: 'flex-end',
    },
});
