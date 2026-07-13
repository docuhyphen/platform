import {Dropdown, Field, Option, OptionOnSelectData, SelectionEvents, Tag, Text} from "@fluentui/react-components";
import {auditCategoryMetaMap} from "../../../components/audit-category-badge/AuditCategoryBadgeStyles.tsx";
import {useAuditExportCategoriesFieldStyles} from "./AuditExportCategoriesFieldStyles.tsx";

interface AuditExportCategoriesFieldProps
{
    categories: string[];
    onChange: (categories: string[]) => void;
}

const AuditExportCategoriesField = ({categories, onChange}: AuditExportCategoriesFieldProps) =>
{
    const styles = useAuditExportCategoriesFieldStyles();
    const selected = (
        <div
            id={"audit-export-selected-categories"}
            className={styles.selected}
        >
            {categories.length === 0
                ? <Text size={200}>Select categories</Text>
                : categories.map((category) => (
                    <Tag
                        id={"audit-export-category-tag-" + category.toLowerCase()}
                        key={category}
                        size={"small"}
                    >{auditCategoryMetaMap[category]?.label ?? category}</Tag>
                ))}
        </div>
    );
    const onSelect = (_event: SelectionEvents, data: OptionOnSelectData) => onChange(data.selectedOptions);

    return (
        <Field
            id={"audit-export-categories-field"}
            label={"Categories"}
            required
        >
            <Dropdown
                id={"audit-export-categories"}
                multiselect
                selectedOptions={categories}
                onOptionSelect={onSelect}
                button={{children: selected}}
            >
                {Object.keys(auditCategoryMetaMap).map((category) => (
                    <Option
                        id={"audit-export-category-option-" + category.toLowerCase()}
                        key={category}
                        value={category}
                    >{auditCategoryMetaMap[category]?.label ?? category}</Option>
                ))}
            </Dropdown>
        </Field>
    );
};

export default AuditExportCategoriesField;
