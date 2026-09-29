import {
    InformationRequestNoticeDeliveryState,
    InformationRequestOperationsAssigneeDto,
    InformationRequestOperationsException,
    InformationRequestOperationsRowDto,
} from "../../models/models.tsx";
import {shareRoleLabels} from "../shared/informationRequestLabels.ts";
import {exceptionLabels, noticeCountLabels, requestStateLabels, slaStatusPresentation} from "./operationsLabels.ts";

export const OPERATIONS_CSV_FILE_NAME = "information-requests.csv";

const HEADER = [
    "Request id",
    "Title",
    "State",
    "Service level",
    "Due",
    "Created",
    "Age in hours",
    "Reminders",
    "Assignees",
    "Notices",
    "Needs attention",
];

const FORMULA_START = /^[=+\-@\t\r]/;
const NEEDS_QUOTES = /[",;\r\n]/;

const cell = (value: string): string =>
{
    const guarded = FORMULA_START.test(value) ? `'${value}` : value;
    return NEEDS_QUOTES.test(guarded) ? `"${guarded.replace(/"/g, "\"\"")}"` : guarded;
};

export const assigneeLabel = (assignee: InformationRequestOperationsAssigneeDto): string =>
    `${assignee.label ?? "Unnamed party"} (${shareRoleLabels[assignee.roleKey]})`;

const counted = <K extends string>(counts: Partial<Record<K, number>>, keys: K[], labels: Record<K, string>): string =>
    keys.filter(key => (counts[key] ?? 0) > 0).map(key => `${counts[key]} ${labels[key]}`).join("; ");

const line = (row: InformationRequestOperationsRowDto): string => [
    row.requestId,
    row.title,
    requestStateLabels[row.state],
    slaStatusPresentation[row.slaStatus].label,
    row.nearestDueAt ?? "",
    row.createdAt,
    String(Math.floor(row.ageSeconds / 3600)),
    String(row.reminderCount),
    row.assignees.map(assigneeLabel).join("; "),
    counted(row.noticeCounts, Object.values(InformationRequestNoticeDeliveryState), noticeCountLabels),
    counted(row.exceptionCounts, Object.values(InformationRequestOperationsException), exceptionLabels),
].map(cell).join(",");

export const operationsCsv = (rows: InformationRequestOperationsRowDto[]): string =>
    [HEADER.map(cell).join(","), ...rows.map(line)].join("\r\n");

export const saveCsvFile = (fileName: string, content: string) =>
{
    const url = window.URL.createObjectURL(new Blob(["﻿", content], {type: "text/csv;charset=utf-8"}));
    const link = document.createElement("a");
    link.href = url;
    link.download = fileName;
    link.click();
    window.URL.revokeObjectURL(url);
};
