import {makeStyles} from '@fluentui/react-components';
import {fieldsFormStyles, fieldsStatusTextStyles} from '../FieldsSharedStyles';

export const useSchemaBindingsEditorStyles = makeStyles({
    fieldGroup: fieldsFormStyles.fieldGroup,
    emptyText: fieldsStatusTextStyles.emptyText,
});
