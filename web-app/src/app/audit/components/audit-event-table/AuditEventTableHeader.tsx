import {
    TableHeader,
    TableHeaderCell,
    TableRow,
} from "@fluentui/react-components";

interface AuditEventTableHeaderProps
{
    className: string;
}

const AuditEventTableHeader = ({
    className,
}: AuditEventTableHeaderProps) => (
    <TableHeader
        id={"audit-event-table-header"}
        className={className}>
        <TableRow id={"audit-event-table-header-row"}>
            <TableHeaderCell id={"audit-event-table-occurred-column"}>
                Occurred
            </TableHeaderCell>
            <TableHeaderCell id={"audit-event-table-category-column"}>
                Category
            </TableHeaderCell>
            <TableHeaderCell id={"audit-event-table-event-column"}>
                Event
            </TableHeaderCell>
            <TableHeaderCell id={"audit-event-table-outcome-column"}>
                Outcome
            </TableHeaderCell>
            <TableHeaderCell id={"audit-event-table-actor-column"}>
                Actor
            </TableHeaderCell>
            <TableHeaderCell id={"audit-event-table-target-column"}>
                Target
            </TableHeaderCell>
        </TableRow>
    </TableHeader>
);

export default AuditEventTableHeader;
