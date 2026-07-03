import {useState} from 'react';
import {Tab, TabList, TabValue} from '@fluentui/react-components';
import {useAuth} from '../../../context/AuthContext';
import {Capability} from '../../models/models';
import {useFieldsTabStyles} from './FieldsTabStyles';
import FieldDefinitionsPanel from './FieldDefinitionsPanel';
import SchemasPanel from './SchemasPanel';

const FieldsTab = () =>
{
    const styles = useFieldsTabStyles();
    const {appUserPersonOrganization, hasCapability} = useAuth();
    const canManage = !!appUserPersonOrganization?.isActive &&
        (hasCapability(Capability.APP_ADMIN) || hasCapability(Capability.ORG_POLICY_MANAGE));

    const [selected, setSelected] = useState<TabValue>('fields');

    return (
        <div id="settings-fields-tab"
             className={styles.container}>
            <TabList id="fields-tab-inner-tabs"
                     selectedValue={selected}
                     onTabSelect={(_, data) => setSelected(data.value)}
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

            {selected === 'fields' && <FieldDefinitionsPanel canManage={canManage}/>}
            {selected === 'schemas' && <SchemasPanel canManage={canManage}/>}
        </div>
    );
};

export default FieldsTab;
