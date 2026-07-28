import {Button, MessageBar, MessageBarBody, Spinner, Tab, TabList, Text} from "@fluentui/react-components";
import {useState} from "react";
import {FieldScopeKind} from "../../models/models.tsx";
import FieldDefinitionDialog from "../../settings/fields-tab/FieldDefinitionDialog.tsx";
import FieldDefinitionsPanel from "../../settings/fields-tab/FieldDefinitionsPanel.tsx";
import SchemaCard from "../../settings/fields-tab/SchemaCard.tsx";
import SchemaEditorDialog from "../../settings/fields-tab/SchemaEditorDialog.tsx";
import {AddIcon} from "../../components/IconBundles.tsx";
import {useFieldsTabStyles} from "../../settings/fields-tab/FieldsTabStyles.tsx";
import {usePlatformContentStyles} from "./PlatformContentStyles.tsx";
import {usePlatformFieldManagement} from "./usePlatformFieldManagement.ts";

type SelectedConfiguration = "fields" | "schemas";

const PlatformFields = () =>
{
    const styles = usePlatformContentStyles();
    const fieldStyles = useFieldsTabStyles();
    const management = usePlatformFieldManagement();
    const [selected, setSelected] = useState<SelectedConfiguration>("fields");

    return (
        <div
            id={"platform-fields"}
            className={styles.tabPanel}>
            <div
                id={"platform-fields-toolbar"}
                className={styles.splitToolbar}>
                <TabList
                    id={"platform-fields-tabs"}
                    selectedValue={selected}
                    onTabSelect={(_, data) => setSelected(data.value as SelectedConfiguration)}>
                    <Tab
                        id={"platform-fields-definitions-tab"}
                        value={"fields"}>
                        Fields
                    </Tab>
                    <Tab
                        id={"platform-fields-schemas-tab"}
                        value={"schemas"}>
                        Schemas
                    </Tab>
                </TabList>
                <Button
                    id={"platform-field-configuration-create"}
                    appearance={"subtle"}
                    shape={"circular"}
                    icon={<AddIcon/>}
                    onClick={() =>
                    {
                        if (selected === "fields") management.setFieldEditorOpen(true);
                        else management.setSchemaEditor("new");
                    }}>
                    {selected === "fields" ? "Create platform field" : "Create platform schema"}
                </Button>
            </div>
            <div
                id={"platform-fields-scrollable-content"}
                className={styles.scrollableContent}>
                {management.error && (
                    <MessageBar
                        id={"platform-fields-error"}
                        intent={"error"}>
                        <MessageBarBody id={"platform-fields-error-body"}>{management.error}</MessageBarBody>
                    </MessageBar>
                )}
                {management.loading ? (
                    <div
                        id={"platform-fields-loading"}
                        className={styles.loading}>
                        <Spinner
                            id={"platform-fields-spinner"}
                            label={"Loading platform field configuration"}/>
                    </div>
                ) : selected === "fields" ? (
                    <FieldDefinitionsPanel
                        definitions={management.fields}
                        viewMode={"cards"}
                        canManage={() => true}
                        loading={false}
                        error={null}
                        onRetire={field => void management.retireField(field)}/>
                ) : (
                    <div
                        id={"platform-schemas-grid"}
                        className={fieldStyles.cardGrid}>
                        {management.schemas.length === 0 ? (
                            <Text id={"platform-schemas-empty"}>No platform schemas are available.</Text>
                        ) : management.schemas.map(schema => (
                            <SchemaCard
                                key={schema.id}
                                schema={schema}
                                canManage={true}
                                viewMode={"cards"}
                                onEdit={item => void management.openSchema(item)}
                                onPublish={item => void management.publishSchema(item)}
                                onNewVersion={item => void management.openSchemaVersion(item)}
                                onRetire={item => void management.retireSchema(item)}/>
                        ))}
                    </div>
                )}
            </div>
            <FieldDefinitionDialog
                open={management.fieldEditorOpen}
                existingNamespaces={management.fields.map(field => field.namespace)}
                enforcedScope={FieldScopeKind.PLATFORM}
                onClose={() => management.setFieldEditorOpen(false)}
                onSaved={management.saved}/>
            <SchemaEditorDialog
                open={management.schemaEditor !== null}
                schema={management.schemaEditor && management.schemaEditor !== "new"
                    ? management.schemaEditor
                    : undefined}
                definitions={management.fields}
                enforcedScope={FieldScopeKind.PLATFORM}
                onClose={() => management.setSchemaEditor(null)}
                onSaved={management.saved}/>
        </div>
    );
};

export default PlatformFields;
