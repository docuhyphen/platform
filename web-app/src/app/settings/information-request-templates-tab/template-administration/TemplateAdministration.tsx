import {useState} from "react";
import {Button, Tab, TabList} from "@fluentui/react-components";
import {AddIcon} from "../../../components/IconBundles.tsx";
import {InformationRequestCapabilitiesDto, InformationRequestTemplateScopeKind} from "../../../models/models.tsx";
import TemplateCreateDialog from "../template-create-dialog/TemplateCreateDialog.tsx";
import TemplateEditor from "../template-editor/TemplateEditor.tsx";
import TemplateList from "../template-list/TemplateList.tsx";
import {useInformationRequestTemplatesTabStyles} from "../InformationRequestTemplatesTabStyles.tsx";
import {useInformationRequestTemplateAdministration} from "../useInformationRequestTemplateAdministration.ts";

interface Props
{
    capabilities: InformationRequestCapabilitiesDto | null;
}

const TemplateAdministration = ({capabilities}: Props) =>
{
    const styles = useInformationRequestTemplatesTabStyles();
    const state = useInformationRequestTemplateAdministration(capabilities);
    const [creating, setCreating] = useState(false);

    if (state.openTemplate && state.commands)
    {
        return (
            <TemplateEditor template={state.openTemplate}
                            schemas={state.schemas}
                            canManage={state.canManageScope}
                            copyTargets={state.copyTargets}
                            commands={state.commands}
                            onClose={state.close}/>
        );
    }

    return (
        <>
            <div id={"information-request-template-header"}
                 className={styles.header}>
                <TabList id={"information-request-template-scope-tabs"}
                         selectedValue={state.scope ?? undefined}
                         onTabSelect={(_, data) => state.setScope(data.value as InformationRequestTemplateScopeKind)}>
                    <Tab id={"information-request-template-personal-scope"}
                         value={InformationRequestTemplateScopeKind.PERSONAL}>
                        My Templates
                    </Tab>
                    {state.hasOrg && (
                        <Tab id={"information-request-template-organization-scope"}
                             value={InformationRequestTemplateScopeKind.ORGANIZATION}>
                            Organization
                        </Tab>
                    )}
                    <Tab id={"information-request-template-platform-scope"}
                         value={InformationRequestTemplateScopeKind.PLATFORM}>
                        Platform
                    </Tab>
                </TabList>
                {state.canManageScope && (
                    <Button id={"information-request-template-create"}
                            appearance={"primary"}
                            shape={"circular"}
                            icon={<AddIcon/>}
                            onClick={() => setCreating(true)}>
                        New Template
                    </Button>
                )}
            </div>
            <div id={"information-request-template-content"}
                 className={styles.content}>
                <TemplateList templates={state.templates}
                              loading={state.loading}
                              error={state.error}
                              onOpen={template => void state.open(template)}/>
            </div>
            {creating && state.scope && (
                <TemplateCreateDialog scopeKind={state.scope}
                                      onCreate={state.create}
                                      onDismiss={() => setCreating(false)}/>
            )}
        </>
    );
};

export default TemplateAdministration;
