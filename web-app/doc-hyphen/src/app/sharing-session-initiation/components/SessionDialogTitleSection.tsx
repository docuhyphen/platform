import React from 'react';
import {Button, Tab, TabList, TabValue, Text} from "@fluentui/react-components";
import {
    DismissRegular,
    DocumentBulletListMultipleRegular,
    DocumentOnePageRegular,
    OptionsRegular,
    PeopleCommunityAddRegular
} from "@fluentui/react-icons";
import {useSharingSessionInitiationStyles} from "../SharingSessionInitiationStyles.tsx";

interface DialogTitleSectionProps {
    sessionInitiatedSuccessfully: boolean;
    choosingTemplate: boolean;
    requestingDocuments: boolean;
    setChoosingTemplate: (value: boolean) => void;
    selectedTab: TabValue;
    onTabSelect: (event: any, data: any) => void;
}

const SessionDialogTitleSection: React.FC<DialogTitleSectionProps> = ({
    sessionInitiatedSuccessfully,
    choosingTemplate,
    requestingDocuments,
    setChoosingTemplate,
    selectedTab,
    onTabSelect
}) => {
    const styles = useSharingSessionInitiationStyles();

    return (
        <>
            <div className={styles.dialogTitle1}>
                {!sessionInitiatedSuccessfully &&
                    <Text size={500}>
                        Initiating Sharing Session (
                        <em>{(requestingDocuments) ? "Requesting" : "Sending"}
                        </em>)
                    </Text>
                }

                {(!choosingTemplate && !sessionInitiatedSuccessfully) &&
                    <Button appearance={"outline"}
                            size={"small"}
                            onClick={() => setChoosingTemplate(true)}>
                        Choose Template
                    </Button>
                }
                {choosingTemplate &&
                    <Button appearance={"primary"}
                            icon={<DismissRegular/>}
                            size={"small"} onClick={() => setChoosingTemplate(false)}>
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