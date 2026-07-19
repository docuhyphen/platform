import React, {useState} from 'react';
import {Button, Tab, TabList, Text} from '@fluentui/react-components';
import {BackIcon} from '../../../components/IconBundles.tsx';
import {useAddPersonPanelStyles} from './AddPersonPanelStyles.tsx';
import RegisteredPersonAccessPanel
    from './registered-person-access-panel/RegisteredPersonAccessPanel.tsx';
import TrustedParticipantPanel from './trusted-participant-panel/TrustedParticipantPanel.tsx';

interface AddPersonPanelProps
{
    exchangeId: string;
    onBack: () => void;
    onPersonAdded: () => void;
}

type PersonSource = 'REGISTERED' | 'TRUSTED';

const AddPersonPanel: React.FC<AddPersonPanelProps> = ({exchangeId, onBack, onPersonAdded}) =>
{
    const styles = useAddPersonPanelStyles();
    const [source, setSource] = useState<PersonSource>('REGISTERED');

    return (
        <div
            id={"add-person-panel"}
            className={styles.container}
        >
            <div
                id={"add-person-panel-header"}
                className={styles.topBar}
            >
                <Button
                    id={"add-person-panel-back-btn"}
                    appearance="subtle"
                    shape="circular"
                    icon={<BackIcon/>}
                    onClick={onBack}
                >
                    Back
                </Button>
                <Text
                    id={"add-person-panel-title"}
                    size={400}
                    weight="semibold"
                >
                    Add person
                </Text>
            </div>
            <TabList
                id={"add-person-source-tabs"}
                selectedValue={source}
                onTabSelect={(_event, data) => setSource(data.value as PersonSource)}
            >
                <Tab
                    id={"add-registered-person-tab"}
                    value={"REGISTERED"}
                >
                    Person
                </Tab>
                <Tab
                    id={"add-trusted-participant-tab"}
                    value={"TRUSTED"}
                >
                    Trusted Organization
                </Tab>
            </TabList>
            {source === 'REGISTERED' ? (
                <RegisteredPersonAccessPanel
                    exchangeId={exchangeId}
                    onCancel={onBack}
                    onAdded={onPersonAdded}
                />
            ) : (
                <TrustedParticipantPanel
                    exchangeId={exchangeId}
                    onCancel={onBack}
                    onInvited={onPersonAdded}
                />
            )}
        </div>
    );
};

export default AddPersonPanel;
