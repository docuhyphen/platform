import {useState} from "react";
import {Button, Field, Input, MessageBar, MessageBarBody, Spinner, Text} from "@fluentui/react-components";
import {PlatformUserSubscriptionPolicy} from "../../../services/types/platformUserSubscriptions.ts";
import UserSubscriptionEditorDialog from "./UserSubscriptionEditorDialog.tsx";
import UserSubscriptionsTable from "./UserSubscriptionsTable.tsx";
import {useUserSubscriptions} from "./useUserSubscriptions.ts";
import {useUserSubscriptionsStyles} from "./UserSubscriptionsStyles.tsx";

const UserSubscriptions = () =>
{
    const styles = useUserSubscriptionsStyles();
    const state = useUserSubscriptions();
    const [editing, setEditing] = useState<PlatformUserSubscriptionPolicy | null>(null);
    const lastItem = Math.min(state.offset + state.pageSize, state.total);

    return (
        <section
            id={"platform-user-subscriptions"}
            className={styles.container}>
            <div
                id={"platform-user-subscriptions-toolbar"}
                className={styles.toolbar}>
                <Field
                    id={"platform-user-subscriptions-search-field"}
                    label={"Search registered users"}>
                    <Input
                        id={"platform-user-subscriptions-search-input"}
                        value={state.query}
                        onChange={(_, data) => state.setQuery(data.value)}/>
                </Field>
            </div>
            {state.error && (
                <MessageBar
                    id={"platform-user-subscriptions-error"}
                    intent={"error"}>
                    <MessageBarBody id={"platform-user-subscriptions-error-body"}>{state.error}</MessageBarBody>
                </MessageBar>
            )}
            {state.loading
                ? <Spinner
                    id={"platform-user-subscriptions-loading"}
                    label={"Loading subscriptions"}/>
                : <UserSubscriptionsTable
                    items={state.items}
                    onEdit={setEditing}/>}
            <div
                id={"platform-user-subscriptions-pagination"}
                className={styles.pagination}>
                <Text id={"platform-user-subscriptions-page-summary"}>
                    {state.total === 0 ? "0" : `${state.offset + 1}-${lastItem}`} of {state.total}
                </Text>
                <Button
                    id={"platform-user-subscriptions-previous"}
                    appearance={"secondary"}
                    shape={"circular"}
                    disabled={state.offset === 0}
                    onClick={() => state.setOffset(Math.max(0, state.offset - state.pageSize))}>
                    Previous
                </Button>
                <Button
                    id={"platform-user-subscriptions-next"}
                    appearance={"secondary"}
                    shape={"circular"}
                    disabled={state.offset + state.pageSize >= state.total}
                    onClick={() => state.setOffset(state.offset + state.pageSize)}>
                    Next
                </Button>
            </div>
            <UserSubscriptionEditorDialog
                user={editing}
                onDismiss={() => setEditing(null)}
                onSave={state.save}/>
        </section>
    );
};

export default UserSubscriptions;
