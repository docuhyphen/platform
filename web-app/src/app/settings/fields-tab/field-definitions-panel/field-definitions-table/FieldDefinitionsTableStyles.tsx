import {makeStyles} from '@fluentui/react-components';
import {fieldsCardStyles, fieldsTableStyles} from '../../FieldsSharedStyles';

export const useFieldDefinitionsTableStyles = makeStyles({
    table: fieldsTableStyles.table,
    th: fieldsTableStyles.th,
    tr: fieldsTableStyles.tr,
    td: fieldsTableStyles.td,
    cardFieldKey: fieldsCardStyles.cardFieldKey,
});
