import {Badge} from "@fluentui/react-components";
import {UserSessionDto} from "../../../models/models.tsx";

interface SessionStatusBadgeProps
{
    session: UserSessionDto;
}

const SessionStatusBadge = ({session}: SessionStatusBadgeProps) =>
{
    if (session.isCurrent && session.isActive)
    {
        return <Badge id={"session-table-row-badge-1"}
                      appearance="filled"
                      color="brand">This device</Badge>;
    }

    if (session.isActive)
    {
        return <Badge id={"session-table-row-badge-2"}
                      appearance="outline"
                      color="success">Active</Badge>;
    }

    if (session.revocationReasonCode === "EXCHANGE_EXPIRED")
    {
        return <Badge id={"session-table-row-badge-3"}
                      appearance="outline"
                      color="warning">Expired</Badge>;
    }

    return <Badge id={"session-table-row-badge-4"}
                  appearance="tint"
                  color="danger">Revoked</Badge>;
};

export default SessionStatusBadge;
