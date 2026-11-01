import {Button, SelectTabData, SelectTabEvent, Tab, TabList, TabValue} from '@fluentui/react-components';
import {AddIcon} from '../../../components/IconBundles';
import {useFieldsTabHeaderStyles} from './FieldsTabHeaderStyles';

interface Props
{
    selected: TabValue;
    canManageOrganization: boolean;
    onTabChange: (event: SelectTabEvent, data: SelectTabData) => void;
    onCreateField: () => void;
    onCreateSchema: () => void;
}

const FieldsTabHeader = ({selected, canManageOrganization, onTabChange, onCreateField, onCreateSchema}: Props) =>
{
    const styles = useFieldsTabHeaderStyles();

    return (
        <div id={"fields-tab-div-2"}
             className={styles.tabsHeaderRow}>
            <TabList id="fields-tab-inner-tabs"
                     selectedValue={selected}
                     onTabSelect={onTabChange}
                     size="small">
                <Tab id="fields-inner-tab-fields"
                     value="fields">
                    Fields
                </Tab>
                <Tab id="fields-inner-tab-schemas"
                     value="schemas">
                    Schemas
                </Tab>
            </TabList>

            {canManageOrganization && selected === 'fields' && (
                <Button id="field-def-create-btn"
                        appearance="subtle"
                        shape="circular"
                        icon={<AddIcon/>}
                        onClick={onCreateField}>
                    New field
                </Button>
            )}
            {canManageOrganization && selected === 'schemas' && (
                <Button id="schema-create-btn"
                        appearance="subtle"
                        shape="circular"
                        icon={<AddIcon/>}
                        onClick={onCreateSchema}>
                    New schema
                </Button>
            )}
        </div>
    );
};

export default FieldsTabHeader;
