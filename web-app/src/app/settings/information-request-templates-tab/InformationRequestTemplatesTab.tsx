import {Button, Tab, TabList, TabValue} from "@fluentui/react-components";
import {AddIcon} from "../../components/IconBundles.tsx";
import {InformationRequestTemplateScopeKind} from "../../models/models.tsx";
import InformationRequestTemplateDraftPanel from "./InformationRequestTemplateDraftPanel.tsx";
import InformationRequestTemplateList from "./InformationRequestTemplateList.tsx";
import {useInformationRequestTemplatesTabStyles} from "./InformationRequestTemplatesTabStyles.tsx";
import {useInformationRequestTemplateAdministration} from "./useInformationRequestTemplateAdministration.ts";

const InformationRequestTemplatesTab = () =>
{
    const styles = useInformationRequestTemplatesTabStyles();
    const state = useInformationRequestTemplateAdministration();

    return (
        <div
            id={"settings-information-request-templates-tab"}
            className={styles.root}
        >
            <div className={styles.stickyBlock}>
                <div className={styles.headerRow}>
                    <TabList
                        id={"information-request-template-scope-tabs"}
                        selectedValue={state.scope}
                        onTabSelect={(_, data) => state.setScope(data.value as TabValue)}
                    >
                        <Tab
                            id={"information-request-template-personal-scope"}
                            value={InformationRequestTemplateScopeKind.PERSONAL}
                        >
                            My Templates
                        </Tab>
                        {state.hasOrg && (
                            <Tab
                                id={"information-request-template-organization-scope"}
                                value={InformationRequestTemplateScopeKind.ORGANIZATION}
                            >
                                Organization
                            </Tab>
                        )}
                    </TabList>
                    <Button
                        id={"information-request-template-create"}
                        appearance={"subtle"}
                        shape={"circular"}
                        icon={<AddIcon/>}
                        onClick={() => void state.createDraft()}
                    >
                        New Template
                    </Button>
                </div>
            </div>
            <div className={styles.content}>
                <InformationRequestTemplateList
                    templates={state.templates}
                    loading={state.loading}
                    error={state.error}
                    onOpenDraft={template => void state.openDraft(template)}
                />
                <InformationRequestTemplateDraftPanel
                    template={state.selectedTemplate}
                    schemas={state.schemas}
                    onSave={state.saveDraft}
                    onPublish={state.publishDraft}
                />
            </div>
        </div>
    );
};

export default InformationRequestTemplatesTab;
