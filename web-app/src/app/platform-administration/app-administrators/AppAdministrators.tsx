import {
    Button,
    MessageBar,
    MessageBarBody,
    MessageBarTitle,
    Spinner,
} from "@fluentui/react-components";
import {PersonAddRegular} from "@fluentui/react-icons";
import AddAdminDialog from "./add-admin-dialog/AddAdminDialog.tsx";
import AppAdministratorsTable from "./app-administrators-table/AppAdministratorsTable.tsx";
import {useAppAdministrators} from "./useAppAdministrators.ts";
import {useAppAdministratorsStyles} from "./AppAdministratorsStyles.tsx";

const AppAdministrators = () =>
{
    const styles = useAppAdministratorsStyles();
    const {
        admins,
        loading,
        error,
        noAccess,
        busy,
        isAddDialogOpen,
        existingAdminUserIds,
        setIsAddDialogOpen,
        handleRevoke,
        handleAddComplete,
    } = useAppAdministrators();

    if (noAccess)
    {
        return (
            <MessageBar
                id={"app-administrators-no-access"}
                intent={"info"}>
                <MessageBarBody id={"app-administrators-no-access-body"}>
                    <MessageBarTitle id={"app-administrators-no-access-title"}>
                        No access
                    </MessageBarTitle>
                    You do not have permission to view or manage App Administrators.
                </MessageBarBody>
            </MessageBar>
        );
    }

    return (
        <div
            id={"app-administrators-container"}
            className={styles.container}>
            {error && (
                <MessageBar
                    id={"app-administrators-error"}
                    intent={"error"}>
                    <MessageBarBody id={"app-administrators-error-body"}>
                        <MessageBarTitle id={"app-administrators-error-title"}>
                            Error
                        </MessageBarTitle>
                        {error}
                    </MessageBarBody>
                </MessageBar>
            )}

            {loading ? (
                <div
                    id={"app-administrators-loading"}
                    className={styles.loading}>
                    <Spinner
                        id={"app-administrators-loading-spinner"}
                        label={"Loading App Administrators"}
                        size={"small"}/>
                </div>
            ) : (
                <>
                    <div
                        id={"app-administrators-toolbar"}
                        className={styles.header}>
                        <Button
                            id={"btn-add-admin"}
                            icon={<PersonAddRegular/>}
                            appearance={"subtle"}
                            shape={"circular"}
                            onClick={() => setIsAddDialogOpen(true)}>
                            Add Admin
                        </Button>
                    </div>
                    <AppAdministratorsTable
                        admins={admins}
                        busy={busy}
                        onRevoke={handleRevoke}/>
                </>
            )}

            <AddAdminDialog
                isOpen={isAddDialogOpen}
                onDismiss={() => setIsAddDialogOpen(false)}
                existingAdminUserIds={existingAdminUserIds}
                onComplete={handleAddComplete}/>
        </div>
    );
};

export default AppAdministrators;
