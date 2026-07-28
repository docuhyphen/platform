import AuditWorkspace from "../audit/AuditWorkspace.tsx";
import {usePlatformAuditStyles} from "./PlatformAuditStyles.tsx";

const PlatformAudit = () =>
{
    const styles = usePlatformAuditStyles();

    return (
        <main
            id={"platform-audit-page"}
            className={styles.container}>
            <div
                id={"platform-audit-workspace-wrapper"}
                className={styles.workspace}>
                <AuditWorkspace
                    fixedScope={"platform"}
                    navigationMode={"sidebar"}/>
            </div>
        </main>
    );
};

export default PlatformAudit;
