import {makeStyles} from '@fluentui/react-components';
import {fieldsFormStyles, fieldsStatusTextStyles} from '../FieldsSharedStyles';

export const useSchemaEditorDialogStyles = makeStyles({
    drawerBody: fieldsFormStyles.drawerBody,
    errorText: fieldsStatusTextStyles.errorText,
});
