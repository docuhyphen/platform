import React from "react";
import {
    Badge,
    Button,
    Card,
    Spinner,
    Text,
} from "@fluentui/react-components";
import {useLinkedProviderCardStyles} from "./LinkedProviderCardStyles.tsx";

interface LinkedProviderCardProps
{
    id: string;
    title: string;
    description: string;
    providerMark: string;
    linked: boolean;
    canUnlink: boolean;
    isActionLoading: boolean;
    onLink: () => void;
    onUnlink: () => void;
}

const LinkedProviderCard: React.FC<LinkedProviderCardProps> = (
    {
        id,
        title,
        description,
        providerMark,
        linked,
        canUnlink,
        isActionLoading,
        onLink,
        onUnlink
    }
) =>
{
    const styles = useLinkedProviderCardStyles();
    const buttonId = linked
        ? `btn-unlink-${id}`
        : `btn-link-${id}`;

    return (
        <Card
            id={id}
            className={styles.card}>
            <div
                id={`${id}-content`}
                className={styles.contentRow}>
                <div
                    id={`${id}-identity`}
                    className={styles.identityBlock}>
                    <div
                        id={`${id}-mark`}
                        className={styles.providerMark}>
                        <Text
                            id={`${id}-mark-text`}
                            size={300}
                            weight={"semibold"}>
                            {providerMark}
                        </Text>
                    </div>

                    <div
                        id={`${id}-meta`}
                        className={styles.metaBlock}>
                        <div
                            id={`${id}-title-row`}
                            className={styles.titleRow}>
                            <Text
                                id={`${id}-title`}
                                size={400}
                                weight={"semibold"}>
                                {title}
                            </Text>
                            <Badge
                                id={`${id}-status`}
                                appearance={linked ? "filled" : "outline"}
                                color={linked ? "success" : "brand"}>
                                {linked ? "Connected" : "Available"}
                            </Badge>
                        </div>

                        <Text
                            id={`${id}-description`}
                            size={200}
                            className={styles.description}>
                            {description}
                        </Text>
                    </div>
                </div>

                <div
                    id={`${id}-actions`}
                    className={styles.actionBlock}>
                    {linked ? (
                        <>
                            <Button
                                id={buttonId}
                                appearance="secondary"
                                disabled={!canUnlink || isActionLoading}
                                shape={"circular"}
                                onClick={onUnlink}>
                                {isActionLoading ? <Spinner size="tiny"/> : "Unlink"}
                            </Button>
                            {!canUnlink && (
                                <Text
                                    id={`${id}-unlink-help`}
                                    size={200}
                                    className={styles.helperText}>
                                    Keep at least one sign-in method connected.
                                </Text>
                            )}
                        </>
                    ) : (
                        <Button
                            id={buttonId}
                            appearance="secondary"
                            disabled={isActionLoading}
                            shape={"circular"}
                            onClick={onLink}>
                            {isActionLoading ? <Spinner size="tiny"/> : "Link"}
                        </Button>
                    )}
                </div>
            </div>
        </Card>
    );
};

export default LinkedProviderCard;
