import {
    Button,
    Caption1,
    Card,
    Text,
    mergeClasses,
} from "@fluentui/react-components";
import {
    ArrowUploadRegular,
    CheckmarkCircleFilled,
    CircleRegular,
    MoreHorizontalRegular,
} from "@fluentui/react-icons";
import {useIndustryDocumentCardsStyles} from "./IndustryDocumentCardsStyles.tsx";

interface IndustryDocumentCardsProps
{
    labels: readonly string[];
}

export function IndustryDocumentCards({labels}: IndustryDocumentCardsProps)
{
    const styles = useIndustryDocumentCardsStyles();

    return (
        <div
            id="industry-demo-document-cards"
            className={styles.documentCards}
        >
            {labels.map((label, index) => (
                <Card
                    id={`industry-demo-document-card-${index}`}
                    key={label}
                    className={mergeClasses(styles.documentCard, index === 0 && styles.selectedDocumentCard)}
                    appearance="outline"
                >
                    <Text
                        id={`industry-demo-document-card-title-${index}`}
                        className={styles.documentCardTitle}
                        size={200}
                        weight="semibold"
                    >
                        {label}
                    </Text>
                    <div
                        id={`industry-demo-document-card-footer-${index}`}
                        className={styles.documentCardFooter}
                    >
                        <div
                            id={`industry-demo-document-card-status-${index}`}
                            className={styles.documentCardStatus}
                        >
                            {index === 0 ? (
                                <CheckmarkCircleFilled
                                    id="industry-demo-uploaded-status-icon"
                                    className={styles.uploadedIcon}
                                    aria-hidden="true"
                                />
                            ) : (
                                <CircleRegular
                                    id={`industry-demo-not-uploaded-status-icon-${index}`}
                                    aria-hidden="true"
                                />
                            )}
                            <Text
                                id={`industry-demo-document-card-status-label-${index}`}
                                size={200}
                                weight="medium"
                            >
                                {index === 0 ? "Uploaded" : "Not Uploaded"}
                            </Text>
                            {index === 0 && (
                                <Caption1
                                    id="industry-demo-document-card-upload-date"
                                    className={styles.uploadDate}
                                >
                                    Today
                                </Caption1>
                            )}
                        </div>
                        <div
                            id={`industry-demo-document-card-actions-${index}`}
                            className={styles.documentCardActions}
                        >
                            <Button
                                id={`industry-demo-document-card-upload-${index}`}
                                appearance="subtle"
                                size="small"
                                shape="circular"
                                icon={<ArrowUploadRegular id={`industry-demo-upload-icon-${index}`}/>}
                            >
                                {index === 0 ? "Re-upload" : "Upload"}
                            </Button>
                            <Button
                                id={`industry-demo-document-card-more-${index}`}
                                appearance="subtle"
                                size="small"
                                shape="circular"
                                aria-label={`More actions for ${label}`}
                                icon={<MoreHorizontalRegular id={`industry-demo-more-icon-${index}`}/>}
                            />
                        </div>
                    </div>
                </Card>
            ))}
        </div>
    );
}
