export type AuditScope =
    | {kind: "organization"; organizationId: string}
    | {kind: "platform"};
