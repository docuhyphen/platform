import {
    Button,
    MessageBar,
    MessageBarBody,
    MessageBarTitle,
    Spinner,
    Text,
} from "@fluentui/react-components";
import OrganizationEditorDialog from "./organization-editor/OrganizationEditorDialog.tsx";
import OrganizationFilters from "./organization-filters/OrganizationFilters.tsx";
import OrganizationsTable from "./organizations-table/OrganizationsTable.tsx";
import {useOrganizationsStyles} from "./OrganizationsStyles.tsx";
import {usePlatformOrganizations} from "./usePlatformOrganizations.ts";

const Organizations = () =>
{
    const styles = useOrganizationsStyles();
    const state = usePlatformOrganizations();
    const firstItem = state.total === 0 ? 0 : state.offset + 1;
    const lastItem = Math.min(state.offset + state.pageSize, state.total);

    if (state.noAccess)
    {
        return (
            <MessageBar
                id={"platform-organizations-no-access"}
                intent={"info"}>
                <MessageBarBody id={"platform-organizations-no-access-body"}>
                    <MessageBarTitle id={"platform-organizations-no-access-title"}>No access</MessageBarTitle>
                    You do not have permission to view platform organization accounts.
                </MessageBarBody>
            </MessageBar>
        );
    }

    return (
        <div
            id={"platform-organizations-container"}
            className={styles.container}>
            <OrganizationFilters
                disabled={state.loading}
                onApply={state.applyFilters}/>
            {state.error && (
                <MessageBar
                    id={"platform-organizations-error"}
                    className={styles.feedback}
                    intent={"error"}>
                    <MessageBarBody id={"platform-organizations-error-body"}>
                        <MessageBarTitle id={"platform-organizations-error-title"}>Error</MessageBarTitle>
                        {state.error}
                    </MessageBarBody>
                </MessageBar>
            )}
            {state.loading ? (
                <div
                    id={"platform-organizations-loading"}
                    className={styles.loading}>
                    <Spinner
                        id={"platform-organizations-loading-spinner"}
                        size={"small"}
                        label={"Loading organizations"}/>
                </div>
            ) : (
                <>
                    <OrganizationsTable
                        organizations={state.organizations}
                        onEdit={state.setSelected}/>
                    <div
                        id={"platform-organizations-pagination"}
                        className={styles.pagination}>
                        <Text id={"platform-organizations-page-summary"}>
                            {firstItem}-{lastItem} of {state.total}
                        </Text>
                        <Button
                            id={"platform-organizations-previous-page"}
                            appearance={"secondary"}
                            shape={"circular"}
                            disabled={state.offset === 0}
                            onClick={() => state.setOffset(Math.max(0, state.offset - state.pageSize))}>
                            Previous
                        </Button>
                        <Button
                            id={"platform-organizations-next-page"}
                            appearance={"secondary"}
                            shape={"circular"}
                            disabled={state.offset + state.pageSize >= state.total}
                            onClick={() => state.setOffset(state.offset + state.pageSize)}>
                            Next
                        </Button>
                    </div>
                </>
            )}
            <OrganizationEditorDialog
                organization={state.selected}
                onDismiss={() => state.setSelected(null)}
                onSaved={() =>
                {
                    state.setSelected(null);
                    void state.reload();
                }}/>
        </div>
    );
};

export default Organizations;
