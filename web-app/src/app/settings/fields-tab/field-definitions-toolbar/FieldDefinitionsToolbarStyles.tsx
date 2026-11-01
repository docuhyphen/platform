import {makeStyles, tokens} from '@fluentui/react-components';
import {fieldsToolbarStyles} from '../FieldsToolbarSharedStyles';

export const useFieldDefinitionsToolbarStyles = makeStyles({
    descriptionText: fieldsToolbarStyles.descriptionText,
    searchRow: fieldsToolbarStyles.searchRow,
    searchRowInputs: fieldsToolbarStyles.searchRowInputs,
    searchField: fieldsToolbarStyles.searchField,
    filterDivider: {
        border: 'none',
        borderTop: `${tokens.strokeWidthThin} solid ${tokens.colorNeutralStroke2}`,
        margin: "0",
    },
});
