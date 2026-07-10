import {Badge} from "@fluentui/react-components";
import {auditCategoryMetaMap, useAuditCategoryBadgeStyles} from "./AuditCategoryBadgeStyles.tsx";

interface AuditCategoryBadgeProps
{
    category: string;
}

/** Small colored Fluent Badge for one AuditCategory value, with a friendly label. */
const AuditCategoryBadge = (
    {
        category,
    }: AuditCategoryBadgeProps
) =>
{
    const styles = useAuditCategoryBadgeStyles();
    const meta = auditCategoryMetaMap[category];

    return (
        <Badge
            id={`audit-category-badge-${category.toLowerCase()}`}
            className={styles.badge}
            appearance="tint"
            color={meta?.color ?? "subtle"}
            shape="circular"
        >
            {meta?.label ?? category}
        </Badge>
    );
};

export default AuditCategoryBadge;
