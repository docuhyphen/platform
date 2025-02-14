import React from 'react';
import { Button, Text } from "@fluentui/react-components";
import { TabList, Tab, TabValue } from "@fluentui/react-components";
import { PeopleCommunityAddRegular, DocumentOnePageRegular, DocumentBulletListMultipleRegular, OptionsRegular } from "@fluentui/react-icons";

interface DialogTitleSectionProps {
    sessionInitiatedSuccessfully: boolean;
    choosingTemplate: boolean;
    setChoosingTemplate: (value: boolean) => void;
    selectedTab: TabValue;
    onTabSelect: (event: any, data: any) => void;
}

const SessionDialogTitleSection: React.FC<DialogTitleSectionProps> = ({
    sessionInitiatedSuccessfully,
    choosingTemplate,
    setChoosingTemplate,
    selectedTab,
    onTabSelect
}) => {
    return (
        <>
            <div id="dialog-title-1">
                {!sessionInitiatedSuccessfully &&
                    <Text size={500}> Initiating Sharing Session </Text>
                }

                {(!choosingTemplate && !sessionInitiatedSuccessfully) &&
                    <Button appearance={"outline"} size={"small"} onClick={() => setChoosingTemplate(true)}>
                        Choose Template
                    </Button>
                }
                {choosingTemplate &&
                    <Button appearance={"primary"} size={"small"} onClick={() => setChoosingTemplate(false)}>
                        Cancel template selection
                    </Button>
                }
            </div>
            {choosingTemplate && <div>Choosing Template</div>}
            {(!choosingTemplate && !sessionInitiatedSuccessfully) &&
                <TabList selectedValue={selectedTab} onTabSelect={onTabSelect}>
                    <Tab id="recipients" icon={<PeopleCommunityAddRegular />} value="recipients-tab">
                        Recipients & Participants
                    </Tab>
                    <Tab id="details" icon={<DocumentOnePageRegular />} value="details-tab">
                        Details
                    </Tab>
                    <Tab id="documents" icon={<DocumentBulletListMultipleRegular />} value="documents-tab">
                        Documents
                    </Tab>
                    <Tab id="options" icon={<OptionsRegular />} value="options-tab">
                        Options
                    </Tab>
                </TabList>
            }
        </>
    );
};

export default SessionDialogTitleSection;