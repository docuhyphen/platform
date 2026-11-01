import {makeStyles} from '@fluentui/react-components';
import {fieldsCardStyles, fieldsStatusTextStyles, fieldsTableStyles} from '../FieldsSharedStyles';

export const useSchemasPanelStyles = makeStyles({
    errorText: fieldsStatusTextStyles.errorText,
    emptyText: fieldsStatusTextStyles.emptyText,
    table: fieldsTableStyles.table,
    th: fieldsTableStyles.th,
    cardGrid: fieldsCardStyles.cardGrid,
});
