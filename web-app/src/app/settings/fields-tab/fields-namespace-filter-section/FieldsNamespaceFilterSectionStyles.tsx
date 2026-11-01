import {makeStyles} from '@fluentui/react-components';
import {fieldsStatusTextStyles} from '../FieldsSharedStyles';
import {fieldsFilterSectionStyles} from '../FieldsToolbarSharedStyles';

export const useFieldsNamespaceFilterSectionStyles = makeStyles({
    filterSection: fieldsFilterSectionStyles.filterSection,
    filterSectionTitle: fieldsFilterSectionStyles.filterSectionTitle,
    filterSectionList: fieldsFilterSectionStyles.filterSectionList,
    emptyText: fieldsStatusTextStyles.emptyText,
});
