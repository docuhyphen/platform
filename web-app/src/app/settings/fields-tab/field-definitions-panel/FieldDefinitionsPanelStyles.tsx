import {makeStyles} from '@fluentui/react-components';
import {fieldsCardStyles, fieldsStatusTextStyles} from '../FieldsSharedStyles';

export const useFieldDefinitionsPanelStyles = makeStyles({
    errorText: fieldsStatusTextStyles.errorText,
    emptyText: fieldsStatusTextStyles.emptyText,
    cardGrid: fieldsCardStyles.cardGrid,
});
