import {makeStyles} from '@fluentui/react-components';
import {fieldsFormStyles, fieldsStatusTextStyles} from '../FieldsSharedStyles';

export const useFieldDefinitionFormBodyStyles = makeStyles({
    drawerBody: fieldsFormStyles.drawerBody,
    errorText: fieldsStatusTextStyles.errorText,
});
