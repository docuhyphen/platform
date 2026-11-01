import {makeStyles} from '@fluentui/react-components';
import {fieldsCardStyles, fieldsTableStyles} from '../../FieldsSharedStyles';

export const useSchemaTableRowStyles = makeStyles({
    tr: fieldsTableStyles.tr,
    td: fieldsTableStyles.td,
    cardFieldKey: fieldsCardStyles.cardFieldKey,
});
